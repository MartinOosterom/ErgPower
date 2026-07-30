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
        return new CoachService(analyzer(), factory, new CoachContext(storage));
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
    void sessionContextIsDistilledGroundedAndGraceful() throws Exception {
        assumeTrue(Files.exists(FIXTURE), "fixture missing");
        SessionStorage.store(new ReplayPm5Source(FIXTURE), storage.resolve("s1"), SessionMeta.of("replay"));

        String ctx = new CoachContext(storage).render("s1");
        assertTrue(ctx.contains("Session context"), "context header present");
        assertTrue(ctx.contains("Piece:"), "piece line present");
        // The fixture is a single continuous row with no HR belt — degrade gracefully, don't fabricate.
        assertTrue(ctx.contains("single piece"), "no-split piece handled");
        assertFalse(ctx.contains("heart rate"), "no HR section when no belt was worn");
        // Distilled: a handful of lines, never a raw curve/sample dump.
        assertTrue(ctx.length() < 1500, "context is compact: " + ctx.length());
        // Unknown session → empty context rather than an error.
        assertTrue(new CoachContext(storage).render("missing").isEmpty(), "missing session → empty context");
    }

    @Test
    void sessionContextRendersSplitsAndHeartRate() throws Exception {
        // Synthesise a 2-split session with a HR belt to exercise the split table + HR drift paths
        // (the recorded fixtures are single continuous pieces without HR).
        Path dir = storage.resolve("multi");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("summary.json"),
                "{\"strokes\":40,\"distanceM\":1000,\"durationS\":200,\"avgPowerW\":205,\"peakPowerW\":260}");
        Files.writeString(dir.resolve("status-general.ndjson"),
                "{\"pmTime\":1.0,\"distanceM\":5,\"workoutDurationType\":128,\"workoutDurationM\":1000,\"dragFactor\":118}\n"
                        + "{\"pmTime\":200.0,\"distanceM\":1000,\"workoutDurationType\":128,\"workoutDurationM\":1000,\"dragFactor\":120}\n");
        Files.writeString(dir.resolve("status-additional1.ndjson"),
                "{\"pmTime\":1.0,\"strokeRate\":30,\"heartRateBpm\":150,\"avgPaceS\":100.0}\n"
                        + "{\"pmTime\":200.0,\"strokeRate\":32,\"heartRateBpm\":178,\"avgPaceS\":100.0}\n");
        Files.writeString(dir.resolve("split.ndjson"),
                "{\"splitTimeS\":100,\"splitDistanceM\":500}\n{\"splitTimeS\":100,\"splitDistanceM\":500}\n");
        Files.writeString(dir.resolve("split-additional.ndjson"),
                "{\"powerW\":210,\"avgStrokeRate\":30,\"workHeartRateBpm\":158}\n"
                        + "{\"powerW\":198,\"avgStrokeRate\":32,\"workHeartRateBpm\":175}\n");

        String ctx = new CoachContext(storage).render("multi");
        assertTrue(ctx.contains("1000 m"), "distance rendered");
        assertTrue(ctx.contains("drag factor 120"), "drag (last positive) rendered");
        assertTrue(ctx.contains("target 1000 m"), "distance target rendered");
        assertTrue(ctx.contains("heart rate avg"), "HR section present with a belt");
        assertTrue(ctx.contains("drift 150→178 bpm"), "HR drift across the piece");
        assertTrue(ctx.contains("Splits (# |"), "per-split table present");
        assertTrue(ctx.contains("158 bpm") && ctx.contains("175 bpm"), "per-split HR rendered");
    }

    @Test
    void unreachableProviderIsAGatewayError() throws Exception {
        assumeTrue(Files.exists(FIXTURE), "fixture missing");
        SessionStorage.store(new ReplayPm5Source(FIXTURE), storage.resolve("s1"), SessionMeta.of("replay"));

        // Ollama configured but pointed at a closed port → the provider call fails → UncheckedIOException (→ 502).
        assertThrows(UncheckedIOException.class, () -> service(Provider.OLLAMA, "http://localhost:1").coach("s1"));
    }
}
