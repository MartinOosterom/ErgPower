package work.zing.ergpower.pm5;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.ValidationReport;

/**
 * End-to-end for the dashboard-storage API: CRUD over {@code /dashboards}, the opaque round-trip of an
 * arbitrary config, 404 on unknown, 400 on an unsafe name, and response conformance to the spec.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DashboardApiTest {

    private static final Path DIR;
    static {
        try {
            DIR = Files.createTempDirectory("ergpower-dashboards");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static final OpenApiInteractionValidator VALIDATOR =
            OpenApiInteractionValidator.createFor("api/openapi.yaml").build();

    @DynamicPropertySource
    static void dashboardsDir(DynamicPropertyRegistry registry) {
        registry.add("ergpower.dashboards.dir", DIR::toString);
    }

    @Value("${local.server.port}")
    int port;

    private WebTestClient web() {
        return WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void crudRoundTripsConfigOpaquely() {
        WebTestClient web = web();
        Map<String, Object> config = Map.of(
                "name", "Race",
                "widgets", List.of(Map.of("id", "w1", "type", "stat", "config", Map.of("metric", "power"),
                        "layout", Map.of("x", 0, "y", 0, "w", 3, "h", 2))),
                "customKey", "kept-verbatim");

        web.put().uri("/api/v1/dashboards/Race").bodyValue(config).exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.name").isEqualTo("Race")
                .jsonPath("$.config.customKey").isEqualTo("kept-verbatim");

        web.get().uri("/api/v1/dashboards/Race").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.config.customKey").isEqualTo("kept-verbatim")
                .jsonPath("$.config.widgets[0].type").isEqualTo("stat");

        web.get().uri("/api/v1/dashboards").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$[?(@.name=='Race')]").exists();

        web.delete().uri("/api/v1/dashboards/Race").exchange().expectStatus().isNoContent();
        web.get().uri("/api/v1/dashboards/Race").exchange().expectStatus().isNotFound();
    }

    @Test
    void unsafeNameIsRejected() {
        web().put().uri("/api/v1/dashboards/e..vil").bodyValue(Map.of("x", 1))
                .exchange().expectStatus().isBadRequest();
    }

    @Test
    void responsesConformToTheSpec() {
        WebTestClient web = web();
        web.put().uri("/api/v1/dashboards/Spec").bodyValue(Map.of("name", "Spec", "widgets", List.of()))
                .exchange().expectStatus().isOk();
        assertConforms("/dashboards", body(web, "/api/v1/dashboards"));
        assertConforms("/dashboards/{name}", body(web, "/api/v1/dashboards/Spec"));
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
