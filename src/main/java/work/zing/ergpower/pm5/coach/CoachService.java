package work.zing.ergpower.pm5.coach;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import work.zing.ergpower.api.model.CoachResult;
import work.zing.ergpower.api.model.FeatureStat;
import work.zing.ergpower.api.model.FeatureTrend;
import work.zing.ergpower.api.model.ScoreMetric;
import work.zing.ergpower.api.model.SessionAnalysis;
import work.zing.ergpower.api.model.TechniqueFlag;
import work.zing.ergpower.api.model.TrendPoint;
import work.zing.ergpower.pm5.analysis.TechniqueAnalyzer;
import work.zing.ergpower.pm5.config.AiProperties;

/**
 * Turns a session's <em>deterministic</em> technique analysis into grounded natural-language coaching
 * (design decisions D2/D6). It reuses {@link TechniqueAnalyzer} for the numbers, renders them plus a
 * fixed Kleshnev rubric into a provider-agnostic prompt, and calls the configured Spring AI
 * {@link ChatModel} through a {@link ChatClient}.
 *
 * <p>The coach is optional: the {@code ChatModel} bean exists only when a provider is configured
 * ({@code spring.ai.model.chat} ≠ {@code none}); absent it, {@link #configured()} is false and the
 * coach is disabled without affecting the deterministic analysis.
 *
 * <p>Grounding: only the structured analysis (scorecard, feature stats, drift trends, fault flags) is
 * sent — never the raw force curves — and the system prompt instructs the model to comment only on the
 * provided numbers.
 */
@Component
public class CoachService {

    /**
     * The role + Kleshnev rubric + grounding instruction. Provider-agnostic: the same system prompt is
     * used for every backend so swapping providers changes no coaching behaviour.
     */
    static final String SYSTEM_PROMPT = """
            You are an expert rowing coach analysing a single erg session's force-curve technique.
            You are given metrics ALREADY COMPUTED from the athlete's force curves using established
            rowing biomechanics (Kleshnev / BioRow). Coach ONLY from these numbers: do not invent
            observations, do not reference data you were not given, and never claim to see curve shape
            beyond what the metrics state.

            Reference targets (Kleshnev):
            - Catch gradient = drive % needed to reach 70% of peak force; a faster catch is lower; target <= 17%.
            - Peak force position = drive % at peak force; target <= 40%; > 55% is a late peak (a fault).
            - Finish plateau = drive % held above 70% of peak after the peak; target 28-40%.
            - Hump index > 1 indicates a force disconnection (a dip mid-drive).
            - Consistency = coefficient of variation (cv) across strokes; lower is steadier (> 0.15 is inconsistent).

            You are also given SESSION CONTEXT (workout target, distance/time, average/peak power, pace,
            stroke rate, drag factor, per-split summary, and heart rate when a belt was worn). The
            force-curve TECHNIQUE remains the subject — use the context to explain it, not to become a
            pacing or training-load advisor:
            - Relate technique/feature changes to pacing, fatigue, and drag (e.g. a late-piece force fade
              alongside a positive split and rising rate = fatigue compensated with rate).
            - Read heart rate only as relative effort or drift; never give medical advice.
            - Weigh the workout type: a fade is expected in a maximal 2k test but a concern in steady state.
            Only use context that is present; if a field is absent, don't mention or assume it.

            Write concise, individualised coaching (about 150-200 words) as flowing prose for the athlete:
            1. Lead with the single most important TECHNIQUE thing to improve, tied to a specific metric and its target.
            2. Briefly acknowledge what is already good.
            3. Give one or two concrete drills or cues for the priority issue.
            4. Mention any drift/fatigue trend only if the numbers show one, using the context to explain why.
            Refer to the athlete's own numbers. Do not output JSON, tables, or bullet lists.""";

    /** Appended to the system prompt in progress mode, when a same-type history is available. */
    static final String PROGRESS_SUFFIX = """


            The athlete's recent SAME-TYPE history is included below the current session (each metric's
            values oldest → newest, the last being this session). Additionally narrate PROGRESS: what has
            improved, plateaued, or regressed versus their own recent baseline — technique-first, and only
            from the provided numbers. Do not compare across different workout types.""";

    private final TechniqueAnalyzer analyzer;
    private final CoachContext context;
    private final CoachHistory history;
    private final Supplier<ChatModel> chatModel;
    private final Environment env;
    private final AiProperties ai;

    /** Production wiring: the {@link ChatModel} is an optional bean (present only when a provider is set). */
    @Autowired
    public CoachService(TechniqueAnalyzer analyzer, CoachContext context, CoachHistory history,
            ObjectProvider<ChatModel> chatModelProvider, Environment env, AiProperties ai) {
        this(analyzer, context, history, chatModelProvider::getIfAvailable, env, ai);
    }

    /** Testable seam: supply the model (or {@code () -> null} for "not configured") directly. */
    CoachService(TechniqueAnalyzer analyzer, CoachContext context, CoachHistory history,
            Supplier<ChatModel> chatModel, Environment env, AiProperties ai) {
        this.analyzer = analyzer;
        this.context = context;
        this.history = history;
        this.chatModel = chatModel;
        this.env = env;
        this.ai = ai;
    }

    /** A "respond in <language>" instruction to append to a system prompt, or "" when unset (English). */
    private String languageSuffix() {
        String lang = ai.languageOrNull();
        return lang == null ? "" : "\n\nRespond in " + lang
                + ". Translate only your prose; keep the metric names and numeric values exactly as given.";
    }

    /** Whether a chat provider is configured (a {@link ChatModel} bean exists). */
    public boolean configured() {
        return chatModel.get() != null;
    }

    /** The configured chat provider id (e.g. {@code "ollama"}), or {@code null} when disabled. */
    public String provider() {
        return configured() ? env.getProperty("spring.ai.model.chat") : null;
    }

    /** The active model name, or {@code null} when disabled. */
    public String modelName() {
        ChatModel model = chatModel.get();
        return model != null ? modelName(model) : null;
    }

    /**
     * Produce coaching for a stored session.
     *
     * @throws CoachUnavailableException if no LLM provider is configured (→ 409)
     * @throws NoSuchElementException    if the session does not exist (→ 404)
     * @throws UncheckedIOException      if the provider call fails (→ 502)
     * @throws IOException               if the session's stored data cannot be read (→ 500)
     */
    public CoachResult coach(String id) throws IOException {
        return coach(id, false);
    }

    /**
     * Produce coaching for a stored session. In {@code progress} mode it also grounds the athlete's
     * recent same-type history from the cross-session index; with no comparable history it falls back to
     * single-session coaching. Throws as {@link #coach(String)}.
     */
    public CoachResult coach(String id, boolean progress) throws IOException {
        ChatModel model = chatModel.get();
        if (model == null) {
            throw new CoachUnavailableException("no LLM provider configured (set spring.ai.model.chat)");
        }
        SessionAnalysis analysis = analyzer.analyze(id); // throws NoSuchElementException for unknown id
        String user = renderAnalysis(analysis) + context.render(id);
        String system = SYSTEM_PROMPT;
        if (progress) {
            String hist = history.block(id);
            if (!hist.isEmpty()) { // only switch to progress narration when there is comparable history
                user += hist;
                system += PROGRESS_SUFFIX;
            }
        }
        system += languageSuffix();
        try {
            String text = ChatClient.create(model).prompt().system(system).user(user).call().content();
            return new CoachResult().model(modelName(model)).text(text);
        } catch (RuntimeException e) {
            // Surface the provider's own message (e.g. an Ollama 402 balance hint) in the 502 detail.
            throw new UncheckedIOException("LLM provider call failed: " + e.getMessage(), new IOException(e));
        }
    }

    /**
     * Progress coaching over an explicitly selected set of sessions (the progress dashboard). The most
     * recent selected session is the "current" piece; the rest form the baseline. Falls back to
     * single-session coaching of the anchor when fewer than two of the selected sessions have scores.
     *
     * @throws CoachUnavailableException if no provider is configured
     * @throws NoSuchElementException    if the selection is empty or the anchor session is unknown
     * @throws IOException               if a session's stored data cannot be read
     */
    public CoachResult coachOverSet(List<String> sessionIds) throws IOException {
        ChatModel model = chatModel.get();
        if (model == null) {
            throw new CoachUnavailableException("no LLM provider configured (set spring.ai.model.chat)");
        }
        if (sessionIds == null || sessionIds.isEmpty()) {
            throw new NoSuchElementException("no sessions selected");
        }
        String anchor = history.anchorOf(sessionIds);
        SessionAnalysis analysis = analyzer.analyze(anchor);
        String hist = history.blockForSet(sessionIds);
        String user = renderAnalysis(analysis) + context.render(anchor) + hist;
        String system = (hist.isEmpty() ? SYSTEM_PROMPT : SYSTEM_PROMPT + PROGRESS_SUFFIX) + languageSuffix();
        try {
            String text = ChatClient.create(model).prompt().system(system).user(user).call().content();
            return new CoachResult().model(modelName(model)).text(text);
        } catch (RuntimeException e) {
            throw new UncheckedIOException("LLM provider call failed: " + e.getMessage(), new IOException(e));
        }
    }

    /** The model id from a {@link ChatModel}'s default options, or {@code "unknown"}. */
    static String modelName(ChatModel model) {
        try {
            var opts = model.getOptions();
            if (opts != null && opts.getModel() != null && !opts.getModel().isBlank()) {
                return opts.getModel();
            }
        } catch (RuntimeException ignored) {
            // some models may not expose default options
        }
        return "unknown";
    }

    /** Render the structured analysis as compact text — the only thing sent to the provider. */
    public static String renderAnalysis(SessionAnalysis a) {
        StringBuilder sb = new StringBuilder();
        sb.append("Session ").append(a.getId()).append(": ")
                .append(a.getStrokes()).append(" strokes analysed.\n");

        if (!Boolean.TRUE.equals(a.getHasCurves())) {
            sb.append("\nNo force curves were recorded for this session, so no technique metrics are available.");
            return sb.toString();
        }

        sb.append("\nScorecard (metric: value; target; result):\n");
        for (ScoreMetric m : orEmpty(a.getScorecard())) {
            sb.append("- ").append(m.getLabel()).append(": ")
                    .append(num(m.getValue())).append(unit(m.getUnit()))
                    .append("; target ").append(target(m.getTargetMin(), m.getTargetMax(), m.getUnit()))
                    .append("; ").append(Boolean.TRUE.equals(m.getPass()) ? "on target" : "OUT OF RANGE");
            if (m.getNote() != null && !m.getNote().isBlank()) {
                sb.append(" (").append(m.getNote()).append(")");
            }
            sb.append("\n");
        }

        sb.append("\nFeature stats (average, then consistency cv across strokes):\n");
        for (FeatureStat f : orEmpty(a.getFeatures())) {
            sb.append("- ").append(f.getLabel()).append(": avg ")
                    .append(num(f.getAvg())).append(unit(f.getUnit()))
                    .append(", cv ").append(num(f.getCv())).append("\n");
        }

        List<FeatureTrend> trends = orEmpty(a.getTrends());
        if (!trends.isEmpty()) {
            sb.append("\nTrends over the session (first analysed stroke -> last):\n");
            for (FeatureTrend t : trends) {
                List<TrendPoint> pts = orEmpty(t.getPoints());
                if (pts.size() < 2) {
                    continue;
                }
                // Short trends (e.g. the per-quartile progressions) render every point; long per-stroke
                // trends render just first -> last.
                String series = pts.size() <= 5
                        ? pts.stream().map(p -> num(p.getValue())).collect(java.util.stream.Collectors.joining(" -> "))
                        : num(pts.get(0).getValue()) + " -> " + num(pts.get(pts.size() - 1).getValue());
                sb.append("- ").append(t.getLabel()).append(": ").append(series).append(unit(t.getUnit())).append("\n");
            }
        }

        List<TechniqueFlag> flags = orEmpty(a.getFlags());
        sb.append("\nFault flags: ");
        if (flags.isEmpty()) {
            sb.append("none.\n");
        } else {
            sb.append("\n");
            for (TechniqueFlag flag : flags) {
                sb.append("- [").append(flag.getSeverity()).append("] ").append(flag.getMessage());
                if (flag.getCount() != null) {
                    sb.append(" (").append(flag.getCount()).append(" strokes)");
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static String num(BigDecimal v) {
        return v == null ? "n/a" : v.stripTrailingZeros().toPlainString();
    }

    private static String unit(String u) {
        return (u == null || u.isBlank()) ? "" : (u.equals("%") ? "%" : " " + u);
    }

    private static String target(BigDecimal min, BigDecimal max, String unit) {
        if (min == null && max == null) {
            return "none";
        }
        if (min == null) {
            return "<= " + num(max) + unit(unit);
        }
        if (max == null) {
            return ">= " + num(min) + unit(unit);
        }
        return num(min) + unit(unit) + "-" + num(max) + unit(unit);
    }
}
