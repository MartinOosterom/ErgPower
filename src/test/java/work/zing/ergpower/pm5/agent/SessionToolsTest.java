package work.zing.ergpower.pm5.agent;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import work.zing.ergpower.pm5.analysis.SessionAnalysisCache;
import work.zing.ergpower.pm5.analysis.TechniqueAnalyzer;
import work.zing.ergpower.pm5.coach.CoachContext;
import work.zing.ergpower.pm5.config.ErgPowerBleProperties;
import work.zing.ergpower.pm5.source.ReplayPm5Source;
import work.zing.ergpower.pm5.storage.SessionMeta;
import work.zing.ergpower.pm5.storage.SessionStorage;

/**
 * The agent's session tools: they read real stored data, and they cannot be steered outside the session
 * store (path traversal / invalid ids are rejected).
 */
class SessionToolsTest {

    private static final Path FIXTURE =
            Path.of("src/test/resources/captures/2026-07-28_rowerg_432234859_289m_28strokes.ndjson");

    @TempDir
    Path storage;

    private SessionTools tools() {
        ErgPowerBleProperties ble = new ErgPowerBleProperties(
                null, null, null, null, new ErgPowerBleProperties.Storage(storage.toString()), null);
        return new SessionTools(ble, new CoachContext(ble), new SessionAnalysisCache(ble, new TechniqueAnalyzer(ble)));
    }

    @Test
    void readsStoredSessionData() throws Exception {
        assumeTrue(Files.exists(FIXTURE), "fixture missing");
        SessionStorage.store(new ReplayPm5Source(FIXTURE), storage.resolve("s1"), SessionMeta.of("replay"));

        assertTrue(tools().overview("s1").contains("Piece:"), "overview from the session context");
        assertTrue(tools().analysis("s1").contains("Scorecard"), "analysis from the cached scores");
        assertTrue(tools().strokes("s1", 0, 600).contains("Strokes"), "strokes window");
        assertTrue(tools().metrics("s1", 0, 600).contains("Metrics"), "metrics window");
    }

    @Test
    void rejectsPathTraversalAndUnknownIds() {
        SessionTools t = tools();
        assertThrows(NoSuchElementException.class, () -> t.overview("../secret"));   // separator → rejected
        assertThrows(NoSuchElementException.class, () -> t.overview(".."));          // escapes the store → rejected
        assertThrows(NoSuchElementException.class, () -> t.analysis("s1/../s1"));    // separator → rejected
        assertThrows(NoSuchElementException.class, () -> t.overview("missing"));     // valid name, no such session
    }
}
