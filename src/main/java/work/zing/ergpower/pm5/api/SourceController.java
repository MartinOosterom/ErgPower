package work.zing.ergpower.pm5.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import work.zing.ergpower.api.SourceApi;
import work.zing.ergpower.api.model.DiscoveredDevice;
import work.zing.ergpower.api.model.SessionSummary;
import work.zing.ergpower.api.model.SourceRequest;
import work.zing.ergpower.api.model.SourceStatus;

/**
 * Implements the generated {@link SourceApi}: list stored sessions, scan for PM5s, and pick/stop the
 * active source ({@code ble} live or {@code replay}). Blocking work (launching the bridge, scanning)
 * runs off the event loop; invalid selections surface as RFC 7807 problems via {@link ResponseStatusException}.
 */
@RestController
public class SourceController implements SourceApi {

    private final SourceManager sources;
    private final SessionCatalog catalog;

    public SourceController(SourceManager sources, SessionCatalog catalog) {
        this.sources = sources;
        this.catalog = catalog;
    }

    @Override
    public Mono<ResponseEntity<Flux<SessionSummary>>> listSessions(ServerWebExchange exchange) {
        return Mono.just(ResponseEntity.ok(Flux.fromIterable(catalog.list())));
    }

    @Override
    public Mono<ResponseEntity<SourceStatus>> getSource(ServerWebExchange exchange) {
        return Mono.just(ResponseEntity.ok(sources.status()));
    }

    @Override
    public Mono<ResponseEntity<SourceStatus>> startSource(Mono<SourceRequest> sourceRequest,
            ServerWebExchange exchange) {
        return sourceRequest
                .flatMap(req -> Mono.fromCallable(() -> startFor(req)).subscribeOn(Schedulers.boundedElastic()))
                .map(ResponseEntity::ok);
    }

    @Override
    public Mono<ResponseEntity<SourceStatus>> stopSource(ServerWebExchange exchange) {
        return Mono.fromCallable(sources::stop).subscribeOn(Schedulers.boundedElastic()).map(ResponseEntity::ok);
    }

    @Override
    public Mono<ResponseEntity<Flux<DiscoveredDevice>>> scanDevices(ServerWebExchange exchange) {
        return Mono.fromCallable(sources::scanDevices).subscribeOn(Schedulers.boundedElastic())
                .map(list -> ResponseEntity.ok(Flux.fromIterable(list)));
    }

    private SourceStatus startFor(SourceRequest req) {
        try {
            if (req.getType() == SourceRequest.TypeEnum.REPLAY) {
                return sources.startReplay(req.getSessionId(),
                        req.getSpeed() == null ? null : req.getSpeed().doubleValue());
            }
            return sources.startBle(req.getDevice());
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
