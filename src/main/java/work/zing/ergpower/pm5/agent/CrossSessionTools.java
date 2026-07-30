package work.zing.ergpower.pm5.agent;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import work.zing.ergpower.api.model.SessionIndexEntry;
import work.zing.ergpower.pm5.analysis.SessionIndex;

/**
 * Read-only {@code @Tool}s that let the session agent roam ACROSS sessions via the cross-session index
 * (change {@code session-agent}): list/filter the athlete's sessions and compare a metric across a set.
 * This is how a question like "how does this compare to my last 2k?" gets answered.
 */
@Component
public class CrossSessionTools {

    private static final int MAX_ROWS = 30;

    private final SessionIndex index;

    public CrossSessionTools(SessionIndex index) {
        this.index = index;
    }

    @Tool(description = "List the athlete's stored rowing sessions (newest first) with their technique scores, "
            + "optionally filtered by workout target type ('time' or 'distance') and a distance band (metres). "
            + "Use to find comparable past sessions.")
    public String listSessions(
            @ToolParam(required = false, description = "'time' or 'distance'") String targetType,
            @ToolParam(required = false, description = "minimum distance in metres") Double distanceMin,
            @ToolParam(required = false, description = "maximum distance in metres") Double distanceMax) {
        try {
            List<SessionIndexEntry> all = index.list(targetType, dec(distanceMin), dec(distanceMax), null, null);
            if (all.isEmpty()) {
                return "No matching sessions.";
            }
            StringBuilder sb = new StringBuilder("Sessions (id | started | type | distance | catch/peak/finish):\n");
            int shown = 0;
            for (SessionIndexEntry e : all) {
                if (shown++ >= MAX_ROWS) {
                    sb.append("- (…").append(all.size() - MAX_ROWS).append(" more; narrow the filter)\n");
                    break;
                }
                sb.append(String.format("- %s | %s | %s | %s m | %s/%s/%s%n",
                        e.getId(), e.getStartedAt(), e.getTargetType().getValue(),
                        e.getDistanceM() != null ? Math.round(e.getDistanceM().doubleValue()) : "?",
                        score(e, "catchGradient"), score(e, "peakPosition"), score(e, "finishPlateau")));
            }
            return sb.toString();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Tool(description = "Compare one metric across several sessions (by id). Technique metrics: catchGradient, "
            + "peakPosition, finishPlateau, meanMaxRatio. Performance metrics (avgPowerW, peakPowerW, distanceM, "
            + "durationS) are only meaningful across the same workout type.")
    public String compareSessions(
            @ToolParam(description = "comma-separated session ids") String sessionIds,
            @ToolParam(description = "metric key") String metric) {
        try {
            Set<String> want = Set.of(sessionIds.split("\\s*,\\s*"));
            List<SessionIndexEntry> all = index.list(null, null, null, null, null);
            StringBuilder sb = new StringBuilder(metric + " by session:\n");
            boolean any = false;
            for (SessionIndexEntry e : all) {
                if (want.contains(e.getId())) {
                    any = true;
                    BigDecimal v = value(e, metric);
                    sb.append("- ").append(e.getId()).append(": ").append(v != null ? v.toPlainString() : "n/a").append("\n");
                }
            }
            return any ? sb.toString() : "None of those session ids were found.";
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static String score(SessionIndexEntry e, String key) {
        BigDecimal v = e.getScores() != null ? e.getScores().get(key) : null;
        return v != null ? v.toPlainString() : "?";
    }

    private static BigDecimal value(SessionIndexEntry e, String metric) {
        BigDecimal s = e.getScores() != null ? e.getScores().get(metric) : null;
        if (s != null) {
            return s;
        }
        return switch (metric) {
            case "avgPowerW" -> e.getAvgPowerW() != null ? BigDecimal.valueOf(e.getAvgPowerW()) : null;
            case "peakPowerW" -> e.getPeakPowerW() != null ? BigDecimal.valueOf(e.getPeakPowerW()) : null;
            case "distanceM" -> e.getDistanceM();
            case "durationS" -> e.getDurationS();
            default -> null;
        };
    }

    private static BigDecimal dec(Double d) {
        return d != null ? BigDecimal.valueOf(d) : null;
    }
}
