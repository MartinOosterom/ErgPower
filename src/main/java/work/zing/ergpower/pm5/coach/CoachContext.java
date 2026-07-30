package work.zing.ergpower.pm5.coach;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import work.zing.ergpower.pm5.config.AthleteProperties;
import work.zing.ergpower.pm5.config.ErgPowerBleProperties;

/**
 * Assembles a compact, distilled <em>session context</em> for the LLM coach (change
 * {@code enrich-coach-context}) from data already stored for a session — the workout target,
 * distance/time, average/peak power, average pace and stroke rate, drag factor, a per-split summary,
 * and heart-rate average + drift when a belt was worn.
 *
 * <p>This is deliberately a handful of interpretable numbers, never raw per-sample series or the force
 * curves — the coach reasons over meaning, not bulk signal. Anything a session lacks (no HR belt, a
 * single piece with no splits, no target) is simply omitted, so a bare steady row still yields context.
 */
@Component
public class CoachContext {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** Cap the per-split rows so a long interval session can't bloat the prompt; the rest is summarised. */
    private static final int MAX_SPLIT_ROWS = 20;
    // Concept2 workout duration types (see GeneralStatus).
    private static final int DURATION_TYPE_TIME = 0, DURATION_TYPE_DISTANCE = 128;

    private final Path storageDir;

    private final AthleteProperties athlete;

    @Autowired
    public CoachContext(ErgPowerBleProperties props, AthleteProperties athlete) {
        this(Path.of(props.storage().dir()), athlete);
    }

    /** Convenience for tests without a profile. */
    public CoachContext(ErgPowerBleProperties props) {
        this(Path.of(props.storage().dir()), EMPTY_ATHLETE);
    }

    CoachContext(Path storageDir) {
        this(storageDir, EMPTY_ATHLETE);
    }

    CoachContext(Path storageDir, AthleteProperties athlete) {
        this.storageDir = storageDir;
        this.athlete = athlete;
    }

    private static final AthleteProperties EMPTY_ATHLETE =
            new AthleteProperties(null, null, null, null, null, null);

    /**
     * Render the session-context block for the given session, or an empty string if the session has no
     * usable context. Never includes raw curves or per-sample series.
     */
    public String render(String id) throws IOException {
        Path dir = storageDir.resolve(id);
        if (!Files.isDirectory(dir)) {
            return "";
        }
        JsonNode summary = readJson(dir.resolve("summary.json"));
        List<JsonNode> general = readLines(dir.resolve("status-general.ndjson"));
        List<JsonNode> add1 = readLines(dir.resolve("status-additional1.ndjson"));
        List<JsonNode> splits = readLines(dir.resolve("split.ndjson"));
        List<JsonNode> splitAdd = readLines(dir.resolve("split-additional.ndjson"));

        double distanceM = num(summary, "distanceM", lastNum(general, "distanceM", -1));
        double durationS = num(summary, "durationS", lastNum(general, "pmTime", -1));
        double avgPowerW = num(summary, "avgPowerW", -1);
        double peakPowerW = num(summary, "peakPowerW", -1);
        double avgPaceS = lastNum(add1, "avgPaceS", -1);
        int avgRate = avgInt(add1, "strokeRate", true);
        int drag = lastPositiveInt(general, "dragFactor");

        StringBuilder sb = new StringBuilder("\nSession context (what happened during the piece):\n");

        // Piece line: distance, time, target, drag.
        StringBuilder piece = new StringBuilder("- Piece: ");
        piece.append(distanceM >= 0 ? Math.round(distanceM) + " m" : "distance n/a");
        if (durationS >= 0) {
            piece.append(" in ").append(clock(durationS));
        }
        String target = target(general);
        if (target != null) {
            piece.append("; target ").append(target);
        }
        if (drag > 0) {
            piece.append("; drag factor ").append(drag);
        }
        sb.append(piece).append(".\n");

        // Averages: power, pace, rate, HR (+ drift).
        List<String> avgs = new ArrayList<>();
        if (avgPowerW >= 0) {
            String p = "power " + Math.round(avgPowerW) + " W"
                    + (peakPowerW >= 0 ? " (peak " + Math.round(peakPowerW) + " W)" : "");
            Double wpk = athlete.wattsPerKg(avgPowerW); // profile-derived (change rower-profile)
            if (wpk != null) {
                p += " = " + String.format(java.util.Locale.ROOT, "%.2f", wpk) + " W/kg";
            }
            avgs.add(p);
        }
        if (avgPaceS > 0) {
            avgs.add("pace " + pace(avgPaceS) + "/500m");
        }
        if (avgRate > 0) {
            avgs.add("stroke rate " + avgRate + " spm");
        }
        int[] hr = heartRate(add1); // {avg, first, last} or null
        if (hr != null) {
            String zone = athlete.hrZone(hr[0]);
            String drift = hr[1] != hr[2] ? " (drift " + hr[1] + "→" + hr[2] + " bpm)" : "";
            avgs.add("heart rate avg " + hr[0] + " bpm" + (zone != null ? " (" + zone + ")" : "") + drift);
        }
        if (!avgs.isEmpty()) {
            sb.append("- Averages: ").append(String.join("; ", avgs)).append(".\n");
        }
        String goal = athlete.goalOrNull(); // profile training goal (framing only)
        if (goal != null) {
            sb.append("- Training goal: ").append(goal).append(".\n");
        }

        // Per-split table (only when splits were recorded).
        appendSplits(sb, splits, splitAdd);

        // If we produced nothing beyond the header + an empty piece line, treat as no context.
        return sb.length() > 0 ? sb.toString() : "";
    }

    private void appendSplits(StringBuilder sb, List<JsonNode> splits, List<JsonNode> splitAdd) {
        if (splits.isEmpty()) {
            sb.append("- Splits: single piece (no splits recorded).\n");
            return;
        }
        int shown = Math.min(splits.size(), MAX_SPLIT_ROWS);
        sb.append("- Splits (# | dist | time | pace/500m | power | rate | HR):\n");
        for (int i = 0; i < shown; i++) {
            JsonNode sp = splits.get(i);
            JsonNode sa = i < splitAdd.size() ? splitAdd.get(i) : null;
            double t = sp.path("splitTimeS").asDouble(-1);
            double d = sp.path("splitDistanceM").asDouble(-1);
            double paceS = (t > 0 && d > 0) ? t * 500.0 / d : -1;
            double power = num(sa, "powerW", -1);
            double rate = num(sa, "avgStrokeRate", -1);
            double shr = num(sa, "workHeartRateBpm", -1);
            sb.append("  ").append(i + 1).append(". ")
                    .append(d >= 0 ? Math.round(d) + " m" : "?").append(" | ")
                    .append(t >= 0 ? clock(t) : "?").append(" | ")
                    .append(paceS > 0 ? pace(paceS) : "?").append(" | ")
                    .append(power >= 0 ? Math.round(power) + " W" : "?").append(" | ")
                    .append(rate > 0 ? Math.round(rate) + " spm" : "?").append(" | ")
                    .append(shr > 0 ? Math.round(shr) + " bpm" : "-").append("\n");
        }
        if (splits.size() > shown) {
            sb.append("  (… ").append(splits.size() - shown).append(" more splits omitted)\n");
        }
    }

    /** The workout target as text (e.g. "5:00 (time)" or "2000 m"), or null if not a fixed target. */
    private static String target(List<JsonNode> general) {
        for (JsonNode g : general) {
            if (!g.hasNonNull("workoutDurationType")) {
                continue;
            }
            int type = g.get("workoutDurationType").asInt();
            if (type == DURATION_TYPE_TIME && g.hasNonNull("workoutDurationS")) {
                double s = g.get("workoutDurationS").asDouble();
                if (s > 0) {
                    return clock(s) + " (time)";
                }
            } else if (type == DURATION_TYPE_DISTANCE && g.hasNonNull("workoutDurationM")) {
                int m = g.get("workoutDurationM").asInt();
                if (m > 0) {
                    return m + " m";
                }
            }
            return null; // duration fields present but no fixed target (e.g. JustRow)
        }
        return null;
    }

    /** {avg, first, last} heart rate over non-null samples, or null when no HR was recorded. */
    private static int[] heartRate(List<JsonNode> add1) {
        int sum = 0, count = 0, first = -1, last = -1;
        for (JsonNode n : add1) {
            if (n.hasNonNull("heartRateBpm")) {
                int v = n.get("heartRateBpm").asInt();
                if (v > 0) {
                    if (first < 0) {
                        first = v;
                    }
                    last = v;
                    sum += v;
                    count++;
                }
            }
        }
        return count > 0 ? new int[] {Math.round((float) sum / count), first, last} : null;
    }

    private static int avgInt(List<JsonNode> lines, String field, boolean positiveOnly) {
        long sum = 0;
        int count = 0;
        for (JsonNode n : lines) {
            if (n.hasNonNull(field)) {
                int v = n.get(field).asInt();
                if (!positiveOnly || v > 0) {
                    sum += v;
                    count++;
                }
            }
        }
        return count > 0 ? (int) Math.round((double) sum / count) : -1;
    }

    private static int lastPositiveInt(List<JsonNode> lines, String field) {
        int v = -1;
        for (JsonNode n : lines) {
            if (n.hasNonNull(field) && n.get(field).asInt() > 0) {
                v = n.get(field).asInt();
            }
        }
        return v;
    }

    private static double lastNum(List<JsonNode> lines, String field, double def) {
        double v = def;
        for (JsonNode n : lines) {
            if (n.hasNonNull(field)) {
                v = n.get(field).asDouble();
            }
        }
        return v;
    }

    private static double num(JsonNode n, String field, double def) {
        return n != null && n.hasNonNull(field) ? n.get(field).asDouble() : def;
    }

    /** Seconds → m:ss (for clock time). */
    private static String clock(double seconds) {
        long s = Math.round(seconds);
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    /** Pace seconds-per-500m → m:ss. */
    private static String pace(double seconds) {
        return clock(seconds);
    }

    private static JsonNode readJson(Path p) throws IOException {
        return Files.exists(p) ? MAPPER.readTree(Files.readString(p)) : null;
    }

    private static List<JsonNode> readLines(Path f) throws IOException {
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
    }
}
