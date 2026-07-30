package work.zing.ergpower.pm5.agent;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import work.zing.ergpower.api.model.SessionAnalysis;
import work.zing.ergpower.pm5.analysis.SessionAnalysisCache;
import work.zing.ergpower.pm5.coach.CoachContext;
import work.zing.ergpower.pm5.coach.CoachService;
import work.zing.ergpower.pm5.config.ErgPowerBleProperties;

/**
 * Read-only {@code @Tool}s that let the session agent (change {@code session-agent}) pull exactly the
 * data a question needs from ONE session — its overview, deterministic analysis, a metrics window, the
 * strokes in a window, or a single stroke's force curve. Because each call is targeted, tools may return
 * raw slices (one stroke's curve) that the one-shot coach could never send.
 *
 * <p>Safety: every tool takes a session id, which is validated to a safe folder name and resolved
 * strictly within the session store (no path traversal); nothing is written.
 */
@Component
public class SessionTools {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,128}");
    private static final int MAX_ROWS = 24; // cap tool output so a wide window can't flood the prompt

    private final Path storageDir;
    private final CoachContext context;
    private final SessionAnalysisCache analysisCache;

    public SessionTools(ErgPowerBleProperties ble, CoachContext context, SessionAnalysisCache analysisCache) {
        this.storageDir = Path.of(ble.storage().dir());
        this.context = context;
        this.analysisCache = analysisCache;
    }

    @Tool(description = "Overview of one rowing session: workout target, distance/time, average/peak power, "
            + "pace, stroke rate, drag factor, a per-split summary, and heart rate if a belt was worn.")
    public String overview(@ToolParam(description = "session id") String sessionId) {
        Path dir = dir(sessionId);
        try {
            String ctx = context.render(sessionId);
            return ctx.isBlank() ? "No overview available for " + sessionId : ctx;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Tool(description = "Deterministic force-curve technique analysis of one session: the Kleshnev scorecard "
            + "(catch gradient, peak position, finish plateau, mean/max ratio), per-feature consistency, drift "
            + "trends, and fault flags. Use this for technique questions.")
    public String analysis(@ToolParam(description = "session id") String sessionId) {
        dir(sessionId);
        try {
            SessionAnalysis a = analysisCache.scores(sessionId);
            if (!Boolean.TRUE.equals(a.getHasCurves())) {
                return "Session " + sessionId + " has no force curves, so no technique analysis is available.";
            }
            return CoachService.renderAnalysis(a);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Tool(description = "Sampled metrics over a time window of one session: distance, pace, stroke rate, heart "
            + "rate and drag between fromSeconds and toSeconds of elapsed time. Use for 'how did X change' "
            + "questions over part of the piece.")
    public String metrics(@ToolParam(description = "session id") String sessionId,
            @ToolParam(description = "window start, seconds of elapsed time") double fromSeconds,
            @ToolParam(description = "window end, seconds of elapsed time") double toSeconds) {
        Path dir = dir(sessionId);
        List<JsonNode> general = read(dir.resolve("status-general.ndjson"));
        List<JsonNode> add1 = read(dir.resolve("status-additional1.ndjson"));
        List<JsonNode> window = new ArrayList<>();
        for (JsonNode g : general) {
            double t = g.path("pmTime").asDouble(-1);
            if (t >= fromSeconds && t <= toSeconds) {
                window.add(g);
            }
        }
        if (window.isEmpty()) {
            return "No metrics between " + fromSeconds + "s and " + toSeconds + "s.";
        }
        StringBuilder sb = new StringBuilder("Metrics " + fromSeconds + "-" + toSeconds + "s (t | dist | pace/500m | rate | HR | drag):\n");
        int stride = Math.max(1, window.size() / MAX_ROWS);
        for (int i = 0; i < window.size(); i += stride) {
            JsonNode g = window.get(i);
            double t = g.path("pmTime").asDouble();
            JsonNode a = nearest(add1, t);
            sb.append(String.format("- %.0fs | %s m | %s | %s spm | %s | drag %s%n",
                    t, num(g, "distanceM"), pace(a), num(a, "strokeRate"), hr(a), num(g, "dragFactor")));
        }
        return sb.toString();
    }

    @Tool(description = "Per-stroke drive metrics within a time window of one session: drive length/time, "
            + "recovery time, peak and average drive force. Use for stroke-by-stroke questions.")
    public String strokes(@ToolParam(description = "session id") String sessionId,
            @ToolParam(description = "window start, seconds") double fromSeconds,
            @ToolParam(description = "window end, seconds") double toSeconds) {
        Path dir = dir(sessionId);
        List<JsonNode> strokes = read(dir.resolve("stroke.ndjson"));
        StringBuilder sb = new StringBuilder("Strokes " + fromSeconds + "-" + toSeconds + "s (# | drive len/time | recovery | peak/avg force):\n");
        int shown = 0;
        for (JsonNode s : strokes) {
            double t = s.path("pmTime").asDouble(-1);
            if (t < fromSeconds || t > toSeconds) {
                continue;
            }
            if (shown++ >= MAX_ROWS) {
                sb.append("- (…more strokes; narrow the window)\n");
                break;
            }
            sb.append(String.format("- #%s | %sm/%ss | rec %ss | peak %sN avg %sN%n",
                    num(s, "strokeCount"), num(s, "driveLengthM"), num(s, "driveTimeS"),
                    num(s, "recoveryTimeS"), num(s, "peakDriveForceN"), num(s, "avgDriveForceN")));
        }
        return shown == 0 ? "No strokes in that window." : sb.toString();
    }

    @Tool(description = "The raw force curve (Newtons across the drive) of a single stroke of one session, "
            + "identified by its stroke number. Use to inspect the exact shape of one stroke.")
    public String forceCurve(@ToolParam(description = "session id") String sessionId,
            @ToolParam(description = "stroke number (strokeCount)") int strokeCount) {
        Path dir = dir(sessionId);
        for (JsonNode n : read(dir.resolve("force-curve.ndjson"))) {
            if (n.path("strokeCount").asInt(-1) == strokeCount) {
                JsonNode arr = n.get("forcesN");
                if (arr != null && arr.isArray()) {
                    List<String> vals = new ArrayList<>();
                    for (JsonNode v : arr) {
                        vals.add(String.valueOf(Math.round(v.asDouble())));
                    }
                    return "Stroke " + strokeCount + " force curve (N, catch→finish): " + String.join(", ", vals);
                }
            }
        }
        return "No force curve found for stroke " + strokeCount + ".";
    }

    // --- helpers ---

    private Path dir(String sessionId) {
        if (sessionId == null || !SAFE_ID.matcher(sessionId).matches()) {
            throw new NoSuchElementException("invalid session id");
        }
        Path dir = storageDir.resolve(sessionId).normalize();
        if (!dir.startsWith(storageDir.normalize()) || !Files.isDirectory(dir)) {
            throw new NoSuchElementException(sessionId);
        }
        return dir;
    }

    private static List<JsonNode> read(Path f) {
        try {
            if (!Files.exists(f)) {
                return List.of();
            }
            List<JsonNode> out = new ArrayList<>();
            for (String line : Files.readAllLines(f)) {
                if (!line.isBlank()) {
                    out.add(MAPPER.readTree(line));
                }
            }
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static JsonNode nearest(List<JsonNode> lines, double t) {
        JsonNode best = null;
        double bestD = Double.MAX_VALUE;
        for (JsonNode n : lines) {
            double d = Math.abs(n.path("pmTime").asDouble(Double.MAX_VALUE) - t);
            if (d < bestD) {
                bestD = d;
                best = n;
            }
        }
        return best;
    }

    private static String num(JsonNode n, String field) {
        return n != null && n.hasNonNull(field) ? n.get(field).asText() : "?";
    }

    private static String pace(JsonNode a) {
        if (a == null || !a.hasNonNull("avgPaceS")) {
            return "?";
        }
        long s = Math.round(a.get("avgPaceS").asDouble());
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    private static String hr(JsonNode a) {
        return a != null && a.hasNonNull("heartRateBpm") ? a.get("heartRateBpm").asText() + " bpm" : "-";
    }
}
