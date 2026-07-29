package work.zing.ergpower.pm5.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import work.zing.ergpower.api.model.ScoreMetric;
import work.zing.ergpower.api.model.SessionAnalysis;
import work.zing.ergpower.pm5.source.ReplayPm5Source;
import work.zing.ergpower.pm5.storage.SessionMeta;
import work.zing.ergpower.pm5.storage.SessionStorage;

/**
 * Decodes the reference fixture into a session, runs the deterministic analysis, and asserts the feature
 * math is plausible: force curves found, a Kleshnev scorecard with in-range values, a mean±band curve,
 * per-stroke resampled curves, and drift trends.
 */
class TechniqueAnalyzerTest {

    private static final Path FIXTURE =
            Path.of("src/test/resources/captures/2026-07-28_rowerg_432234859_289m_28strokes.ndjson");

    @TempDir
    Path storage;

    @Test
    void analysesForceCurvesPlausibly() throws Exception {
        assumeTrue(Files.exists(FIXTURE), "fixture missing");
        SessionStorage.store(new ReplayPm5Source(FIXTURE), storage.resolve("s1"), SessionMeta.of("replay"));

        SessionAnalysis a = new TechniqueAnalyzer(storage).analyze("s1");

        assertTrue(Boolean.TRUE.equals(a.getHasCurves()), "should have force curves");
        assertTrue(a.getStrokes() > 0, "some strokes analysed");
        assertEquals(a.getStrokes(), a.getCurves().size(), "one resampled curve per stroke");
        assertEquals(a.getBins().intValue(), a.getMeanCurve().size(), "mean curve has one point per bin");
        assertFalse(a.getScorecard().isEmpty(), "scorecard populated");
        assertFalse(a.getTrends().isEmpty(), "drift trends populated");

        ScoreMetric peakPos = a.getScorecard().stream()
                .filter(s -> s.getKey().equals("peakPosition")).findFirst().orElseThrow();
        double v = peakPos.getValue().doubleValue();
        assertTrue(v >= 0 && v <= 100, "peak position is a valid % of the drive: " + v);
        assertNotNull(peakPos.getPass(), "scored against a target");
    }

    @Test
    void unknownSessionThrows() {
        assertThrows(NoSuchElementException.class, () -> new TechniqueAnalyzer(storage).analyze("missing"));
    }
}
