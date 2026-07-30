package work.zing.ergpower.pm5.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import work.zing.ergpower.api.model.SessionIndexEntry;
import work.zing.ergpower.api.model.SessionIndexEntry.TargetTypeEnum;
import work.zing.ergpower.api.model.SessionTrendPoint;
import work.zing.ergpower.pm5.source.ReplayPm5Source;
import work.zing.ergpower.pm5.storage.SessionMeta;
import work.zing.ergpower.pm5.storage.SessionStorage;

/**
 * The cross-session index: builds rows over the analysis cache, filters, trends with the two lenses
 * (technique spans all; performance scoped), persists a rebuildable rollup.
 */
class SessionIndexTest {

    private static final Path FIXTURE =
            Path.of("src/test/resources/captures/2026-07-28_rowerg_432234859_289m_28strokes.ndjson");

    @TempDir
    Path storage;

    private SessionIndex index() {
        return new SessionIndex(storage, new SessionAnalysisCache(storage, new TechniqueAnalyzer(storage)));
    }

    /** A distance session with a summary but no force curves (so no technique scores). */
    private void writeDistanceSession(String id) throws Exception {
        Path dir = storage.resolve(id);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("summary.json"),
                "{\"strokes\":300,\"distanceM\":2000,\"durationS\":420,\"avgPowerW\":180,\"peakPowerW\":220}");
        Files.writeString(dir.resolve("status-general.ndjson"),
                "{\"pmTime\":1.0,\"workoutDurationType\":128,\"workoutDurationM\":2000,\"distanceM\":5}\n");
    }

    @Test
    void listsScoresFiltersAndTrends() throws Exception {
        assumeTrue(Files.exists(FIXTURE), "fixture missing");
        SessionStorage.store(new ReplayPm5Source(FIXTURE), storage.resolve("s1_curves"), SessionMeta.of("replay"));
        writeDistanceSession("s2_dist");

        List<SessionIndexEntry> all = index().list(null, null, null, null, null);
        assertEquals(2, all.size(), "both sessions indexed");
        assertTrue(Files.exists(storage.resolve(SessionIndex.ROLLUP_FILE)), "rollup persisted");

        SessionIndexEntry curves = all.stream().filter(e -> e.getId().equals("s1_curves")).findFirst().orElseThrow();
        assertTrue(Boolean.TRUE.equals(curves.getHasCurves()), "fixture has curves");
        assertTrue(curves.getScores().containsKey("catchGradient"), "technique scores indexed");

        SessionIndexEntry dist = all.stream().filter(e -> e.getId().equals("s2_dist")).findFirst().orElseThrow();
        assertEquals(TargetTypeEnum.DISTANCE, dist.getTargetType(), "distance target detected");
        assertEquals(0, dist.getTargetValue().compareTo(new java.math.BigDecimal("2000")), "target value");
        assertFalse(Boolean.TRUE.equals(dist.getHasCurves()), "no curves for the distance session");

        // Filter by distance band: only the longer piece (the fixture is a ~289 m distance workout too).
        assertEquals(List.of("s2_dist"),
                index().list(null, new java.math.BigDecimal("1000"), null, null, null)
                        .stream().map(SessionIndexEntry::getId).toList());

        // Technique trend spans only sessions that have the score (the curves session).
        List<SessionTrendPoint> catch_ = index().trend("catchGradient", null, null, null, null, null);
        assertEquals(1, catch_.size(), "only the session with curves contributes a technique point");
        assertEquals("s1_curves", catch_.get(0).getSessionId());

        // Performance trend spans both, oldest first (s1_curves sorts before s2_dist by folder name).
        List<SessionTrendPoint> power = index().trend("avgPowerW", null, null, null, null, null);
        assertEquals(2, power.size(), "both sessions have a performance point");
        assertEquals("s1_curves", power.get(0).getSessionId(), "oldest first");
    }

    @Test
    void scalesToHundredsOfSessions() throws Exception {
        // Hundreds of (curve-free) sessions: the first listing builds the rollup, later ones read it.
        for (int i = 0; i < 250; i++) {
            writeDistanceSession(String.format("s%04d", i));
        }
        assertEquals(250, index().list(null, null, null, null, null).size(), "all sessions indexed");
        assertTrue(Files.exists(storage.resolve(SessionIndex.ROLLUP_FILE)), "rollup persisted");
        // A repeat listing is served from the single rollup file (id-set unchanged), not re-derived.
        assertEquals(250, index().list(null, null, null, null, null).size());
        // Trends still work at scale.
        assertEquals(250, index().trend("avgPowerW", null, null, null, null, null).size());
    }

    @Test
    void rollupIsRebuildable() throws Exception {
        writeDistanceSession("s2_dist");
        int first = index().list(null, null, null, null, null).size();
        // Delete the rollup and rebuild from the folders → same rows.
        Files.deleteIfExists(storage.resolve(SessionIndex.ROLLUP_FILE));
        assertEquals(first, index().rebuild().size(), "rebuild reproduces the index from session folders");
    }
}
