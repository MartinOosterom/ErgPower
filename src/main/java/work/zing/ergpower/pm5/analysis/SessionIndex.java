package work.zing.ergpower.pm5.analysis;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import work.zing.ergpower.api.model.ScoreMetric;
import work.zing.ergpower.api.model.SessionAnalysis;
import work.zing.ergpower.api.model.SessionIndexEntry;
import work.zing.ergpower.api.model.SessionIndexEntry.TargetTypeEnum;
import work.zing.ergpower.api.model.SessionTrendPoint;
import work.zing.ergpower.pm5.config.ErgPowerBleProperties;

/**
 * The cross-session index (change {@code cross-session-index}): each session's summary plus its cached
 * technique scores, for fast filtered listing and metric-over-time trends across hundreds of sessions.
 *
 * <p>Built from the per-session {@link SessionAnalysisCache} (so the expensive analysis is computed once)
 * and persisted as a rebuildable rollup ({@code sessions-index.json}) at the storage root. The rollup is
 * a cache: it is rebuilt whenever the analyzer version or the set of sessions changes, and deleting it
 * reproduces the same rows from the session folders.
 *
 * <p>Two comparison lenses: technique-shape metrics are normalized (% of the drive) and comparable
 * across any pieces; performance metrics (power, pace) are only comparable within a workout type, so
 * callers scope them with the filters.
 */
@Component
public class SessionIndex {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(SessionIndex.class);
    private static final int REBUILD_LOG_THRESHOLD = 50; // only narrate large rebuilds

    static final String ROLLUP_FILE = "sessions-index.json";
    /** Technique-shape score keys — comparable across any pieces (see {@link TechniqueAnalyzer}). */
    static final Set<String> TECHNIQUE_METRICS =
            Set.of("catchGradient", "peakPosition", "finishPlateau", "meanMaxRatio");
    // Concept2 workout duration types (see GeneralStatus).
    private static final int DURATION_TYPE_TIME = 0, DURATION_TYPE_DISTANCE = 128;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path storageDir;
    private final SessionAnalysisCache cache;

    @Autowired
    public SessionIndex(ErgPowerBleProperties ble, SessionAnalysisCache cache) {
        this(Path.of(ble.storage().dir()), cache);
    }

    SessionIndex(Path storageDir, SessionAnalysisCache cache) {
        this.storageDir = storageDir;
        this.cache = cache;
    }

    /** Filtered index, newest first. */
    public List<SessionIndexEntry> list(String targetType, BigDecimal distanceMin, BigDecimal distanceMax,
            String from, String to) throws IOException {
        return current().stream()
                .filter(e -> matches(e, targetType, distanceMin, distanceMax, from, to))
                .toList();
    }

    /**
     * A metric over time (oldest first) across the sessions matching the filters. Technique metrics may
     * span the whole log; performance metrics should be scoped via the filters (see class doc).
     */
    public List<SessionTrendPoint> trend(String metric, String targetType, BigDecimal distanceMin,
            BigDecimal distanceMax, String from, String to) throws IOException {
        List<SessionIndexEntry> matched = new ArrayList<>(list(targetType, distanceMin, distanceMax, from, to));
        Collections.reverse(matched); // list() is newest-first; a trend reads oldest-first
        List<SessionTrendPoint> points = new ArrayList<>();
        for (SessionIndexEntry e : matched) {
            BigDecimal v = metricValue(e, metric);
            if (v != null) {
                points.add(new SessionTrendPoint(e.getId(), v).startedAt(e.getStartedAt()));
            }
        }
        return points;
    }

    /** Re-derive the whole index from the session folders and rewrite the rollup. */
    public synchronized List<SessionIndexEntry> rebuild() throws IOException {
        List<Path> dirs = sessionDirs();
        if (dirs.size() >= REBUILD_LOG_THRESHOLD) {
            LOG.info("Rebuilding cross-session index over {} sessions…", dirs.size());
        }
        List<SessionIndexEntry> entries = new ArrayList<>();
        for (Path dir : dirs) {
            entries.add(buildEntry(dir));
        }
        writeRollup(entries);
        if (dirs.size() >= REBUILD_LOG_THRESHOLD) {
            LOG.info("Cross-session index rebuilt: {} entries.", entries.size());
        }
        return entries;
    }

    /** The current index — served from the rollup when it is fresh, else rebuilt. */
    private synchronized List<SessionIndexEntry> current() throws IOException {
        Path rollup = storageDir.resolve(ROLLUP_FILE);
        Set<String> ids = new LinkedHashSet<>();
        for (Path d : sessionDirs()) {
            ids.add(d.getFileName().toString());
        }
        if (Files.exists(rollup)) {
            JsonNode root = MAPPER.readTree(Files.readString(rollup));
            if (root.path("analyzerVersion").asInt(-1) == TechniqueAnalyzer.ANALYZER_VERSION) {
                List<SessionIndexEntry> cached = new ArrayList<>();
                Set<String> cachedIds = new LinkedHashSet<>();
                for (JsonNode n : root.withArray("entries")) {
                    SessionIndexEntry e = MAPPER.treeToValue(n, SessionIndexEntry.class);
                    cached.add(e);
                    cachedIds.add(e.getId());
                }
                if (cachedIds.equals(ids)) {
                    return cached;
                }
            }
        }
        return rebuild();
    }

    private List<Path> sessionDirs() throws IOException {
        if (!Files.isDirectory(storageDir)) {
            return List.of();
        }
        try (Stream<Path> dirs = Files.list(storageDir)) {
            return dirs.filter(Files::isDirectory)
                    .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
                    .toList();
        }
    }

    private SessionIndexEntry buildEntry(Path dir) throws IOException {
        String id = dir.getFileName().toString();
        SessionAnalysis scores = cache.scores(id); // computed-once, cached
        SessionIndexEntry e = new SessionIndexEntry(id, TargetTypeEnum.NONE,
                Boolean.TRUE.equals(scores.getHasCurves()), Files.exists(dir.resolve("raw.ndjson")));

        JsonNode session = readJson(dir.resolve("session.json"));
        if (session != null && session.hasNonNull("startedAt")) {
            e.startedAt(session.get("startedAt").asText());
        }
        JsonNode sum = readJson(dir.resolve("summary.json"));
        if (sum != null) {
            e.distanceM(dec(sum, "distanceM")).durationS(dec(sum, "durationS"))
             .avgPowerW(intOrNull(sum, "avgPowerW")).peakPowerW(intOrNull(sum, "peakPowerW"));
        }
        applyTarget(e, readFirstLine(dir.resolve("status-general.ndjson")));
        for (ScoreMetric m : scores.getScorecard() != null ? scores.getScorecard() : List.<ScoreMetric>of()) {
            if (m.getKey() != null && m.getValue() != null) {
                e.putScoresItem(m.getKey(), m.getValue());
            }
        }
        return e;
    }

    private static void applyTarget(SessionIndexEntry e, JsonNode general) {
        if (general == null || !general.hasNonNull("workoutDurationType")) {
            return;
        }
        int type = general.get("workoutDurationType").asInt();
        if (type == DURATION_TYPE_TIME) {
            e.targetType(TargetTypeEnum.TIME);
            if (general.hasNonNull("workoutDurationS")) {
                e.targetValue(BigDecimal.valueOf(general.get("workoutDurationS").asDouble()));
            }
        } else if (type == DURATION_TYPE_DISTANCE) {
            e.targetType(TargetTypeEnum.DISTANCE);
            if (general.hasNonNull("workoutDurationM")) {
                e.targetValue(BigDecimal.valueOf(general.get("workoutDurationM").asDouble()));
            }
        }
    }

    private static BigDecimal metricValue(SessionIndexEntry e, String metric) {
        if (TECHNIQUE_METRICS.contains(metric)) {
            return e.getScores() != null ? e.getScores().get(metric) : null;
        }
        return switch (metric) {
            case "avgPowerW" -> e.getAvgPowerW() != null ? BigDecimal.valueOf(e.getAvgPowerW()) : null;
            case "peakPowerW" -> e.getPeakPowerW() != null ? BigDecimal.valueOf(e.getPeakPowerW()) : null;
            case "distanceM" -> e.getDistanceM();
            case "durationS" -> e.getDurationS();
            default -> null;
        };
    }

    private static boolean matches(SessionIndexEntry e, String targetType, BigDecimal distanceMin,
            BigDecimal distanceMax, String from, String to) {
        if (targetType != null && !targetType.isBlank()
                && (e.getTargetType() == null || !e.getTargetType().getValue().equalsIgnoreCase(targetType))) {
            return false;
        }
        BigDecimal dist = e.getDistanceM();
        if (distanceMin != null && (dist == null || dist.compareTo(distanceMin) < 0)) {
            return false;
        }
        if (distanceMax != null && (dist == null || dist.compareTo(distanceMax) > 0)) {
            return false;
        }
        String started = e.getStartedAt();
        if (from != null && !from.isBlank() && (started == null || started.compareTo(from) < 0)) {
            return false;
        }
        if (to != null && !to.isBlank() && (started == null || started.compareTo(to) > 0)) {
            return false;
        }
        return true;
    }

    private void writeRollup(List<SessionIndexEntry> entries) throws IOException {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("analyzerVersion", TechniqueAnalyzer.ANALYZER_VERSION);
        ArrayNode arr = root.putArray("entries");
        for (SessionIndexEntry e : entries) {
            arr.add(MAPPER.valueToTree(e));
        }
        Path target = storageDir.resolve(ROLLUP_FILE);
        Path tmp = target.resolveSibling(ROLLUP_FILE + ".tmp");
        Files.write(tmp, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(root));
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private static JsonNode readJson(Path p) throws IOException {
        return Files.exists(p) ? MAPPER.readTree(Files.readString(p)) : null;
    }

    private static JsonNode readFirstLine(Path p) throws IOException {
        if (!Files.exists(p)) {
            return null;
        }
        for (String line : Files.readAllLines(p)) {
            if (!line.isBlank()) {
                return MAPPER.readTree(line);
            }
        }
        return null;
    }

    private static BigDecimal dec(JsonNode n, String field) {
        return n.hasNonNull(field) ? BigDecimal.valueOf(n.get(field).asDouble()) : null;
    }

    private static Integer intOrNull(JsonNode n, String field) {
        return n.hasNonNull(field) ? n.get(field).asInt() : null;
    }
}
