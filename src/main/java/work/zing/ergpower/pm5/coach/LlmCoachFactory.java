package work.zing.ergpower.pm5.coach;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import work.zing.ergpower.pm5.config.LlmProperties;

/**
 * Selects the configured {@link LlmCoach} from {@link LlmProperties} (design decision D1). This is the
 * single place that knows the provider set, so the rest of the app depends only on the interface and
 * on {@link #configured()}. When the provider is {@code none} the coach is disabled and
 * {@link #coach()} must not be called.
 */
@Component
public class LlmCoachFactory {

    private final LlmProperties props;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http;

    public LlmCoachFactory(LlmProperties props) {
        this.props = props;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    /** Whether a provider is configured (so the coach is available). */
    public boolean configured() {
        return props.enabled();
    }

    /** The configured provider id (e.g. {@code "ollama"}), or {@code null} when disabled. */
    public String provider() {
        return props.providerId();
    }

    /** The effective model, or {@code null} when disabled. */
    public String model() {
        return props.enabled() ? props.resolvedModel() : null;
    }

    /**
     * Build a coach for the configured provider.
     *
     * @throws IllegalStateException if no provider is configured — call {@link #configured()} first
     */
    public LlmCoach coach() {
        return switch (props.provider()) {
            case OLLAMA -> new OllamaCoach(props, http, json);
            case OPENAI -> new OpenAiCoach(props, http, json);
            case ANTHROPIC -> new AnthropicCoach(props, http, json);
            case NONE -> throw new IllegalStateException("no LLM provider configured");
        };
    }
}
