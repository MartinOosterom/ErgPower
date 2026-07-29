package work.zing.ergpower.pm5.coach;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import work.zing.ergpower.pm5.config.LlmProperties;

/**
 * {@link LlmCoach} backed by a local <a href="https://ollama.com">Ollama</a> daemon via its
 * {@code POST /api/generate} endpoint. This is the privacy-first default: with Ollama configured,
 * the analysis never leaves the machine. Base URL defaults to {@code http://localhost:11434}.
 */
final class OllamaCoach implements LlmCoach {

    private final LlmProperties props;
    private final HttpClient http;
    private final ObjectMapper json;

    OllamaCoach(LlmProperties props, HttpClient http, ObjectMapper json) {
        this.props = props;
        this.http = http;
        this.json = json;
    }

    @Override
    public String complete(String system, String user) throws IOException, InterruptedException {
        // /api/generate takes a single prompt plus an optional system field; stream:false yields one JSON object.
        byte[] body = json.writeValueAsBytes(Map.of(
                "model", props.resolvedModel(),
                "system", system,
                "prompt", user,
                "stream", false));
        HttpRequest req = HttpRequest.newBuilder(URI.create(props.resolvedBaseUrl() + "/api/generate"))
                .timeout(props.timeout())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        HttpResponse<byte[]> res = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (res.statusCode() / 100 != 2) {
            throw new IOException("ollama " + res.statusCode() + ": " + new String(res.body()));
        }
        JsonNode root = json.readTree(res.body());
        return root.path("response").asText("").strip();
    }
}
