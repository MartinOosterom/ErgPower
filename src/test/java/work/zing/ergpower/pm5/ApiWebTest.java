package work.zing.ergpower.pm5;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

import work.zing.ergpower.pm5.api.LiveState;
import work.zing.ergpower.pm5.source.ReplayPm5Source;

/**
 * Drives the real reactive server: feeds the fixture into {@link LiveState}, then hits the endpoints
 * under {@code /api/v1} via {@link WebTestClient}, validating routing, base-path, and DTO serialization.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiWebTest {

    private static final Path FIXTURE =
            Path.of("src/test/resources/captures/2026-07-28_rowerg_432234859_289m_28strokes.ndjson");

    @Value("${local.server.port}")
    int port;

    @Autowired
    LiveState liveState;

    @Test
    void endpointsServeUnderApiV1() {
        assumeTrue(Files.exists(FIXTURE), "fixture missing");
        new ReplayPm5Source(FIXTURE).events().toIterable().forEach(liveState::onEvent);

        WebTestClient web = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();

        web.get().uri("/api/v1/connection").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.state").isEqualTo("DISCONNECTED");

        web.get().uri("/api/v1/live/snapshot").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.metrics.distanceM").exists()
                .jsonPath("$.metrics.powerW").exists()
                .jsonPath("$.lastForceCurve.forcesN").isArray();
    }
}
