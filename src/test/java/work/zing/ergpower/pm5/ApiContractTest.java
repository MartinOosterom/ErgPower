package work.zing.ergpower.pm5;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.ValidationReport;

import work.zing.ergpower.pm5.api.LiveState;
import work.zing.ergpower.pm5.source.ReplayPm5Source;

/**
 * Strict contract test: loads {@code api/openapi.yaml} (fails the build if the spec is invalid — the
 * lint gate) and validates the actual {@code /connection} and {@code /live/snapshot} response bodies
 * against the spec's response schemas (task 4.1). Paths are as declared in the spec (server is /api/v1).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiContractTest {

    private static final Path FIXTURE =
            Path.of("src/test/resources/captures/2026-07-28_rowerg_432234859_289m_28strokes.ndjson");

    // Loading the validator parses + validates the OpenAPI document (build gate for spec validity).
    private static final OpenApiInteractionValidator VALIDATOR =
            OpenApiInteractionValidator.createFor("api/openapi.yaml").build();

    @Value("${local.server.port}")
    int port;

    @Autowired
    LiveState liveState;

    @Test
    void responsesConformToTheOpenApiSchemas() {
        assumeTrue(Files.exists(FIXTURE), "fixture missing");
        new ReplayPm5Source(FIXTURE).events().toIterable().forEach(liveState::onEvent);
        WebTestClient web = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();

        assertConforms("/connection", body(web, "/api/v1/connection"));
        assertConforms("/live/snapshot", body(web, "/api/v1/live/snapshot"));
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
