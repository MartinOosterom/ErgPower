package work.zing.ergpower.pm5.coach;

import java.io.IOException;

/**
 * A single-shot chat completion against one LLM backend — the pluggable seam of the optional coach
 * (change {@code add-llm-coach}). Implementations ({@code OllamaCoach}, {@code OpenAiCoach},
 * {@code AnthropicCoach}) do only the request/response mapping for their provider; the coaching
 * prompt and Kleshnev rubric are built provider-agnostically in {@link CoachService}, so swapping
 * providers changes no coaching logic.
 */
public interface LlmCoach {

    /**
     * Complete once and return the assistant's plain text.
     *
     * @param system the role/rubric instruction (grounding: "comment only on the provided numbers")
     * @param user   the session's deterministic analysis rendered as text
     * @return the coaching text
     * @throws IOException          on transport/HTTP failure or a non-2xx response
     * @throws InterruptedException if the calling thread is interrupted while awaiting the response
     */
    String complete(String system, String user) throws IOException, InterruptedException;
}
