package work.zing.ergpower.pm5.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import work.zing.ergpower.api.model.SessionAnalysis;
import work.zing.ergpower.pm5.source.ReplayPm5Source;
import work.zing.ergpower.pm5.storage.SessionMeta;
import work.zing.ergpower.pm5.storage.SessionStorage;

/**
 * The per-session analysis cache: compute-once, reuse, invalidate on version change, and re-derivable —
 * storing the compact scores (no heavy curve arrays) as {@code analysis.json}.
 */
class SessionAnalysisCacheTest {

    private static final Path FIXTURE =
            Path.of("src/test/resources/captures/2026-07-28_rowerg_432234859_289m_28strokes.ndjson");

    @TempDir
    Path storage;

    private SessionAnalysisCache cache() {
        return new SessionAnalysisCache(storage, new TechniqueAnalyzer(storage));
    }

    @Test
    void computesCachesAndReuses() throws Exception {
        assumeTrue(Files.exists(FIXTURE), "fixture missing");
        SessionStorage.store(new ReplayPm5Source(FIXTURE), storage.resolve("s1"), SessionMeta.of("replay"));
        Path cacheFile = storage.resolve("s1").resolve(SessionAnalysisCache.CACHE_FILE);

        assertFalse(Files.exists(cacheFile), "no cache before first read");
        SessionAnalysis a = cache().scores("s1");
        assertTrue(Files.exists(cacheFile), "cache written on first read");
        assertFalse(a.getScorecard().isEmpty(), "scores present");
        assertNull(a.getCurves(), "compact: heavy per-stroke curves dropped");
        assertNull(a.getMeanCurve(), "compact: mean curve dropped");

        // Second read is served from the cache and matches.
        SessionAnalysis again = cache().scores("s1");
        assertEquals(a.getScorecard().size(), again.getScorecard().size());
    }

    @Test
    void staleVersionRecomputes() throws Exception {
        assumeTrue(Files.exists(FIXTURE), "fixture missing");
        SessionStorage.store(new ReplayPm5Source(FIXTURE), storage.resolve("s1"), SessionMeta.of("replay"));
        Path cacheFile = storage.resolve("s1").resolve(SessionAnalysisCache.CACHE_FILE);

        // A cache stamped with a wrong version must be ignored and rewritten to the current version.
        Files.writeString(cacheFile, "{\"analyzerVersion\":-1,\"analysis\":{\"id\":\"s1\"}}");
        SessionAnalysis a = cache().scores("s1");
        assertFalse(a.getScorecard().isEmpty(), "recomputed rather than served stale");
        assertTrue(Files.readString(cacheFile).contains("\"analyzerVersion\" : " + TechniqueAnalyzer.ANALYZER_VERSION),
                "cache rewritten at the current version");
    }

    @Test
    void unknownSessionThrows() {
        assertThrows(NoSuchElementException.class, () -> cache().scores("missing"));
    }
}
