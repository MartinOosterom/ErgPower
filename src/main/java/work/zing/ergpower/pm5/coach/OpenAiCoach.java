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
 * {@link LlmCoach} for OpenAI and any OpenAI-compatible endpoint (LM Studio, vLLM, OpenRouter, …) via
 * {@code POST /v1/chat/completions}. Point {@code ergpower.llm.base-url} at the runner and set
 * {@code api-key} where required. System + user are sent as two chat messages.
 */
final class OpenAiCoach implements LlmCoach {

    private final LlmProperties props;
    private final HttpClient http;
    private final ObjectMapper json;

    OpenAiCoach(LlmProperties props, HttpClient http, ObjectMapper json) {
        this.props = props;
        this.http = http;
        this.json = json;
    }

    @Override
    public String complete(String system, String user) throws IOException, InterruptedException {
        byte[] body = json.writeValueAsBytes(Map.of(
                "model", props.resolvedModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", user))));
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(props.resolvedBaseUrl() + "/v1/chat/completions"))
                .timeout(props.timeout())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        if (props.apiKey() != null && !props.apiKey().isBlank()) {
            b.header("Authorization", "Bearer " + props.apiKey());
        }
        HttpResponse<byte[]> res = http.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
        if (res.statusCode() / 100 != 2) {
            throw new IOException("openai " + res.statusCode() + ": " + new String(res.body()));
        }
        JsonNode root = json.readTree(res.body());
        return root.path("choices").path(0).path("message").path("content").asText("").strip();
    }
}
