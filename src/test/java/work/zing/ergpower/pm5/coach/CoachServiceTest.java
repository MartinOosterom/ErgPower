package work.zing.ergpower.pm5.coach;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import work.zing.ergpower.api.model.SessionAnalysis;
import work.zing.ergpower.pm5.analysis.TechniqueAnalyzer;
import work.zing.ergpower.pm5.config.ErgPowerBleProperties;
import work.zing.ergpower.pm5.config.LlmProperties;
import work.zing.ergpower.pm5.config.LlmProperties.Provider;
import work.zing.ergpower.pm5.source.ReplayPm5Source;
import work.zing.ergpower.pm5.storage.SessionMeta;
import work.zing.ergpower.pm5.storage.SessionStorage;

/**
 * Verifies the coach's deterministic behaviour that needs no live LLM: it is disabled with no provider
 * (409 path), the prompt is grounded in the structured analysis (not raw curves), and a provider that
 * cannot be reached surfaces as a gateway error (502 path).
 */
class CoachServiceTest {

    private static final Path FIXTURE =
            Path.of("src/test/resources/captures/2026-07-28_rowerg_432234859_289m_28strokes.ndjson");

    @TempDir
    Path storage;

    private static LlmProperties props(Provider p, String baseUrl) {
        return new LlmProperties(p, null, baseUrl, null, Duration.ofSeconds(5));
    }

    /** Analyzer pointed at the temp storage dir (only the storage path is exercised here). */
    private TechniqueAnalyzer analyzer() {
        ErgPowerBleProperties ble = new ErgPowerBleProperties(
                null, null, null, null, new ErgPowerBleProperties.Storage(storage.toString()), null);
        return new TechniqueAnalyzer(ble);
    }

    private CoachService service(Provider p, String baseUrl) {
        LlmCoachFactory factory = new LlmCoachFactory(props(p, baseUrl));
        return new CoachService(analyzer(), factory);
    }

    @Test
    void disabledWithoutProvider() {
        // No provider → the coach is unavailable (409), checked before any session lookup.
        assertThrows(CoachUnavailableException.class, () -> service(Provider.NONE, null).coach("anything"));
    }

    @Test
    void promptIsGroundedInStructuredAnalysis() throws Exception {
        assumeTrue(Files.exists(FIXTURE), "fixture missing");
        SessionStorage.store(new ReplayPm5Source(FIXTURE), storage.resolve("s1"), SessionMeta.of("replay"));

        SessionAnalysis a = analyzer().analyze("s1");
        String prompt = CoachService.renderAnalysis(a);

        assertTrue(prompt.contains("Scorecard"), "prompt carries the scorecard");
        assertTrue(prompt.contains("Feature stats"), "prompt carries the feature stats");
        assertTrue(prompt.contains(a.getStrokes() + " strokes"), "prompt states the stroke count");
        // Grounding: only the compact structured analysis is rendered — never the raw curve samples
        // (a dump of every stroke's samples would be many times larger than this bounded summary).
        assertTrue(prompt.length() < 4000, "prompt is a compact summary, not a curve dump: " + prompt.length());
    }

    @Test
    void unreachableProviderIsAGatewayError() throws Exception {
        assumeTrue(Files.exists(FIXTURE), "fixture missing");
        SessionStorage.store(new ReplayPm5Source(FIXTURE), storage.resolve("s1"), SessionMeta.of("replay"));

        // Ollama configured but pointed at a closed port → the provider call fails → UncheckedIOException (→ 502).
        assertThrows(UncheckedIOException.class, () -> service(Provider.OLLAMA, "http://localhost:1").coach("s1"));
    }
}
