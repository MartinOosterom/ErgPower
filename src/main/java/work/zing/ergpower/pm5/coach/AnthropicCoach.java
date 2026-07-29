package work.zing.ergpower.pm5.coach;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import work.zing.ergpower.pm5.config.LlmProperties;

/**
 * {@link LlmCoach} for the Anthropic Messages API ({@code POST /v1/messages}). The rubric goes in the
 * top-level {@code system} field and the analysis is the single user message; auth is the
 * {@code x-api-key} header with a pinned {@code anthropic-version}.
 */
final class AnthropicCoach implements LlmCoach {

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final LlmProperties props;
    private final HttpClient http;
    private final ObjectMapper json;

    AnthropicCoach(LlmProperties props, HttpClient http, ObjectMapper json) {
        this.props = props;
        this.http = http;
        this.json = json;
    }

    @Override
    public String complete(String system, String user) throws IOException, InterruptedException {
        byte[] body = json.writeValueAsBytes(Map.of(
                "model", props.resolvedModel(),
                "max_tokens", 1024,
                "system", system,
                "messages", List.of(Map.of("role", "user", "content", user))));
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(props.resolvedBaseUrl() + "/v1/messages"))
                .timeout(props.timeout())
                .header("Content-Type", "application/json")
                .header("anthropic-version", ANTHROPIC_VERSION)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        if (props.apiKey() != null && !props.apiKey().isBlank()) {
            b.header("x-api-key", props.apiKey());
        }
        HttpResponse<byte[]> res = http.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
        if (res.statusCode() / 100 != 2) {
            throw new IOException("anthropic " + res.statusCode() + ": " + new String(res.body()));
        }
        JsonNode root = json.readTree(res.body());
        // content is a list of blocks; concatenate the text blocks.
        StringBuilder sb = new StringBuilder();
        for (JsonNode block : root.path("content")) {
            if ("text".equals(block.path("type").asText())) {
                sb.append(block.path("text").asText());
            }
        }
        return sb.toString().strip();
    }
}
