package work.zing.ergpower.pm5.api;

import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import work.zing.ergpower.api.model.LiveSnapshot;

/**
 * The live endpoints. {@code /live/snapshot} uses the generated DTOs; {@code /live/stream} is
 * hand-mapped because SSE named events can't be expressed through the generated interface (design D1).
 * Paths are relative to {@code spring.webflux.base-path=/api/v1}.
 */
@RestController
public class LiveController {

    private final LiveState liveState;

    public LiveController(LiveState liveState) {
        this.liveState = liveState;
    }

    @GetMapping(value = "/live/snapshot", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<LiveSnapshot> snapshot() {
        return Mono.just(liveState.snapshot());
    }

    @GetMapping(value = "/live/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> stream() {
        return liveState.liveEvents();
    }
}
