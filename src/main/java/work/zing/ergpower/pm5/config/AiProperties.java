package work.zing.ergpower.pm5.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cross-cutting settings for the AI features (change {@code ai-language-and-markdown}), bound from
 * {@code ergpower.ai.*} — typically the git-ignored local config.
 *
 * @param language a natural-language name (e.g. {@code Dutch}) the coach and agent should answer in;
 *                 blank/unset means English. Only prose is translated — metric names and numbers are kept.
 */
@ConfigurationProperties(prefix = "ergpower.ai")
public record AiProperties(String language) {

    /** The configured language name, or {@code null} when unset/blank (i.e. English). */
    public String languageOrNull() {
        return (language != null && !language.isBlank()) ? language.strip() : null;
    }
}
