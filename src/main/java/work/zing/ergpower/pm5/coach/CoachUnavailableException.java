package work.zing.ergpower.pm5.coach;

/** Thrown when coaching is requested but no LLM provider is configured — mapped to HTTP 409. */
public class CoachUnavailableException extends RuntimeException {
    public CoachUnavailableException(String message) {
        super(message);
    }
}
