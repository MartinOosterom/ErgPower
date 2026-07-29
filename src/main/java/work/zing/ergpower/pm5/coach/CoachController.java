package work.zing.ergpower.pm5.coach;

import java.io.UncheckedIOException;
import java.util.NoSuchElementException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import work.zing.ergpower.api.CoachApi;
import work.zing.ergpower.api.model.CoachResult;
import work.zing.ergpower.api.model.LlmStatus;

/**
 * Implements the generated {@link CoachApi}: the optional LLM coach on top of the deterministic
 * analysis (change {@code add-llm-coach}).
 *
 * <ul>
 *   <li>{@code GET /integrations/llm} — a cheap status so the UI shows the panel only when configured.</li>
 *   <li>{@code GET /sessions/{id}/coach} — grounded coaching; 409 when no provider is set, 404 for an
 *       unknown session, 502 if the provider call fails. A read, run off the event loop.</li>
 * </ul>
 */
@RestController
public class CoachController implements CoachApi {

    private final CoachService coachService;
    private final LlmCoachFactory factory;

    public CoachController(CoachService coachService, LlmCoachFactory factory) {
        this.coachService = coachService;
        this.factory = factory;
    }

    @Override
    public Mono<ResponseEntity<LlmStatus>> getLlmStatus(ServerWebExchange exchange) {
        LlmStatus status = new LlmStatus()
                .configured(factory.configured())
                .provider(factory.provider())
                .model(factory.model());
        return Mono.just(ResponseEntity.ok(status));
    }

    @Override
    public Mono<ResponseEntity<CoachResult>> getSessionCoach(String id, ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            try {
                return coachService.coach(id);
            } catch (CoachUnavailableException e) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
            } catch (NoSuchElementException e) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no such session: " + id);
            } catch (UncheckedIOException e) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "LLM provider error: " + e.getMessage());
            }
        }).subscribeOn(Schedulers.boundedElastic()).map(ResponseEntity::ok);
    }
}
