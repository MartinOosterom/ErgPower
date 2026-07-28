package work.zing.ergpower.pm5;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.ValidationReport;

import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

/**
 * End-to-end for source control (add-source-control): seeds a stored session in a temp storage dir,
 * then exercises {@code GET /sessions}, replay via {@code POST /source} (SSE must carry live metrics
 * and force curves), {@code DELETE /source}, the 400 on a non-existent session, and validates the new
 * response bodies against {@code api/openapi.yaml}. No PM5 or bridge is involved — replay only.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SourceControlTest {

    private static final Path FIXTURE =
            Path.of("src/test/resources/captures/2026-07-28_rowerg_432234859_289m_28strokes.ndjson");
    private static final String SESSION_ID = "session-2026-07-28T10-00-00";

    private static final Path STORAGE;
    static {
        try {
            STORAGE = Files.createTempDirectory("ergpower-sessions");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static final OpenApiInteractionValidator VALIDATOR =
            OpenApiInteractionValidator.createFor("api/openapi.yaml").build();

    @DynamicPropertySource
    static void storageDir(DynamicPropertyRegistry registry) {
        registry.add("ergpower.ble.storage.dir", STORAGE::toString);
    }

    @Value("${local.server.port}")
    int port;

    @BeforeAll
    static void seedSession() throws IOException {
        assumeTrue(Files.exists(FIXTURE), "fixture missing");
        Path dir = STORAGE.resolve(SESSION_ID);
        Files.createDirectories(dir);
        Files.copy(FIXTURE, dir.resolve("raw.ndjson"));
        Files.writeString(dir.resolve("session.json"),
                "{\"startedAt\":\"2026-07-28T10:00:00Z\",\"device\":\"PM5 432234859 Row\",\"firmware\":\"rev-x\"}");
        Files.writeString(dir.resolve("summary.json"),
                "{\"strokes\":28,\"distanceM\":289.0,\"durationS\":90.0,\"avgPowerW\":150,\"peakPowerW\":300}");
    }

    private WebTestClient web() {
        return WebTestClient.bindToServer().baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(30)).build();
    }

    @Test
    void listsTheStoredSession() {
        web().get().uri("/api/v1/sessions").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].id").isEqualTo(SESSION_ID)
                .jsonPath("$[0].replayable").isEqualTo(true)
                .jsonPath("$[0].strokes").isEqualTo(28)
                .jsonPath("$[0].distanceM").isEqualTo(289.0);
    }

    @Test
    void replaySourceStreamsMetricsAndForceCurvesThenStops() {
        WebTestClient web = web();

        Flux<ServerSentEvent<String>> stream = web.get().uri("/api/v1/live/stream")
                .accept(MediaType.TEXT_EVENT_STREAM).exchange()
                .expectStatus().isOk()
                .returnResult(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .getResponseBody();

        // Start the replay shortly AFTER the SSE subscription is attached (the sink has no history).
        List<String> names = stream.map(ServerSentEvent::event)
                .filter(n -> "metrics".equals(n) || "forceCurve".equals(n))
                .distinct()
                .take(2)
                .doOnSubscribe(s -> Schedulers.boundedElastic().schedule(() -> startReplay(web), 1, TimeUnit.SECONDS))
                .collectList()
                .block(Duration.ofSeconds(25));

        assertTrue(names != null && names.contains("metrics"), "expected live metrics over SSE");
        assertTrue(names.contains("forceCurve"), "expected force curves over SSE");

        web.delete().uri("/api/v1/source").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.sourceType").isEqualTo("NONE");
    }

    @Test
    void nonReplayableSessionIsRejected() {
        web().post().uri("/api/v1/source")
                .bodyValue(Map.of("type", "replay", "sessionId", "does-not-exist"))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void newResponsesConformToTheSpec() {
        WebTestClient web = web();
        assertConforms("/sessions", body(web, "/api/v1/sessions"));
        assertConforms("/source", body(web, "/api/v1/source"));
    }

    private void startReplay(WebTestClient web) {
        web.post().uri("/api/v1/source")
                .bodyValue(Map.of("type", "replay", "sessionId", SESSION_ID, "speed", 25))
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.sourceType").isEqualTo("REPLAY");
    }

    private String body(WebTestClient web, String uri) {
        return web.get().uri(uri).exchange()
                .expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();
    }

    private void assertConforms(String specPath, String jsonBody) {
        ValidationReport report = VALIDATOR.validateResponse(specPath, Request.Method.GET,
                SimpleResponse.Builder.ok().withContentType("application/json").withBody(jsonBody).build());
        assertFalse(report.hasErrors(), () -> specPath + " response violates the spec: " + report);
    }
}
