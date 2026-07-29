package work.zing.ergpower.pm5.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Externalised configuration for the <em>optional</em> LLM coach (change {@code add-llm-coach}). The
 * coach narrates the deterministic technique analysis; it is a pure add-on and disabled unless a
 * provider is selected here.
 *
 * <p>Bound from {@code ergpower.llm.*}. {@link Provider#NONE} (the default) means the coach is off
 * everywhere and the deterministic analysis is entirely unaffected. Credentials ({@link #apiKey()})
 * should come from the git-ignored {@code ./config/ergpower.local.properties}, never the repository.
 *
 * <p>{@link #model()} and {@link #baseUrl()} are optional: when blank they fall back to a
 * per-provider default (see {@link #resolvedModel()} / {@link #resolvedBaseUrl()}).
 */
@ConfigurationProperties(prefix = "ergpower.llm")
public record LlmProperties(
        @DefaultValue("NONE") Provider provider,
        String model,
        String baseUrl,
        String apiKey,
        @DefaultValue("60s") Duration timeout) {

    /** Which LLM backend narrates the analysis. {@code OPENAI} also covers any OpenAI-compatible base-url. */
    public enum Provider {
        /** No provider — the coach is disabled (default). */
        NONE,
        /** Local Ollama daemon ({@code /api/generate}); nothing leaves the machine. */
        OLLAMA,
        /** OpenAI or any OpenAI-compatible endpoint ({@code /v1/chat/completions}). */
        OPENAI,
        /** Anthropic Messages API ({@code /v1/messages}). */
        ANTHROPIC
    }

    /** True when a provider is configured (i.e. not {@link Provider#NONE}). */
    public boolean enabled() {
        return provider != Provider.NONE;
    }

    /** The provider id for the API/status (e.g. {@code "ollama"}), or {@code null} when disabled. */
    public String providerId() {
        return enabled() ? provider.name().toLowerCase() : null;
    }

    /** The effective model, defaulting per provider when {@link #model()} is unset. */
    public String resolvedModel() {
        if (model != null && !model.isBlank()) {
            return model;
        }
        return switch (provider) {
            case OLLAMA -> "llama3.1";
            case OPENAI -> "gpt-4o-mini";
            case ANTHROPIC -> "claude-3-5-sonnet-latest";
            case NONE -> null;
        };
    }

    /** The effective base URL (no trailing slash), defaulting per provider when {@link #baseUrl()} is unset. */
    public String resolvedBaseUrl() {
        String base = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl : switch (provider) {
            case OLLAMA -> "http://localhost:11434";
            case OPENAI -> "https://api.openai.com";
            case ANTHROPIC -> "https://api.anthropic.com";
            case NONE -> null;
        };
        if (base != null && base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }
}
