package work.zing.ergpower.pm5.coach;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import work.zing.ergpower.pm5.analysis.SessionAnalysisCache;
import work.zing.ergpower.pm5.analysis.SessionIndex;
import work.zing.ergpower.pm5.analysis.TechniqueAnalyzer;
import work.zing.ergpower.pm5.config.ErgPowerBleProperties;
import work.zing.ergpower.pm5.source.ReplayPm5Source;
import work.zing.ergpower.pm5.storage.SessionMeta;
import work.zing.ergpower.pm5.storage.SessionStorage;

/**
 * The progress history block: a same-type trend when there are enough comparable sessions, and a clean
 * empty fallback otherwise.
 */
class CoachHistoryTest {

    private static final Path FIXTURE =
            Path.of("src/test/resources/captures/2026-07-28_rowerg_432234859_289m_28strokes.ndjson");

    @TempDir
    Path storage;

    private CoachHistory history() {
        ErgPowerBleProperties ble = new ErgPowerBleProperties(
                null, null, null, null, new ErgPowerBleProperties.Storage(storage.toString()), null);
        return new CoachHistory(new SessionIndex(ble, new SessionAnalysisCache(ble, new TechniqueAnalyzer(ble))));
    }

    private void storeFixture(String id) throws Exception {
        SessionStorage.store(new ReplayPm5Source(FIXTURE), storage.resolve(id), SessionMeta.of("replay"));
    }

    @Test
    void buildsSameTypeTrendBlock() throws Exception {
        assumeTrue(Files.exists(FIXTURE), "fixture missing");
        // Three comparable same-distance pieces (the fixture is a ~289 m distance workout).
        storeFixture("row-a");
        storeFixture("row-b");
        storeFixture("row-c");

        String block = history().block("row-c");
        assertTrue(block.contains("same-type history"), "history header");
        assertTrue(block.contains("3 comparable distance pieces"), "counts comparable pieces");
        assertTrue(block.contains("catch gradient:") && block.contains("→"), "renders a technique trend");
        assertTrue(block.contains("average power:"), "within-type performance line");
    }

    @Test
    void emptyWhenInsufficientHistory() throws Exception {
        assumeTrue(Files.exists(FIXTURE), "fixture missing");
        storeFixture("only-one");
        assertEquals("", history().block("only-one"), "one session → no trend, fall back to single-session");
    }

    @Test
    void blockForSetOverExplicitSelection() throws Exception {
        assumeTrue(Files.exists(FIXTURE), "fixture missing");
        storeFixture("sel-a");
        storeFixture("sel-b");
        storeFixture("sel-c");
        storeFixture("not-selected");

        String block = history().blockForSet(java.util.List.of("sel-a", "sel-b", "sel-c"));
        assertTrue(block.contains("Selected sessions — 3 pieces"), "renders the chosen set");
        assertTrue(block.contains("catch gradient:") && block.contains("→"), "trend over the set");
        assertEquals("", history().blockForSet(java.util.List.of("sel-a")), "fewer than 2 → empty");
    }
}
