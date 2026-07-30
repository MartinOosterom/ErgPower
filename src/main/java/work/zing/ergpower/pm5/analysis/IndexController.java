package work.zing.ergpower.pm5.analysis;

import java.math.BigDecimal;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import work.zing.ergpower.api.IndexApi;
import work.zing.ergpower.api.model.SessionIndexEntry;
import work.zing.ergpower.api.model.SessionTrendPoint;

/**
 * Implements the generated {@link IndexApi}: cross-session listing with technique scores and
 * metric-over-time trends (change {@code cross-session-index}). Reads — the index build/rollup runs
 * off the event loop.
 */
@RestController
public class IndexController implements IndexApi {

    private final SessionIndex index;

    public IndexController(SessionIndex index) {
        this.index = index;
    }

    @Override
    public Mono<ResponseEntity<Flux<SessionIndexEntry>>> getSessionIndex(String targetType,
            BigDecimal distanceMin, BigDecimal distanceMax, String from, String to, ServerWebExchange exchange) {
        return Mono.fromCallable(() -> index.list(targetType, distanceMin, distanceMax, from, to))
                .subscribeOn(Schedulers.boundedElastic())
                .map(list -> ResponseEntity.ok(Flux.fromIterable(list)));
    }

    @Override
    public Mono<ResponseEntity<Flux<SessionTrendPoint>>> getTrend(String metric, String targetType,
            BigDecimal distanceMin, BigDecimal distanceMax, String from, String to, ServerWebExchange exchange) {
        return Mono.fromCallable(() -> index.trend(metric, targetType, distanceMin, distanceMax, from, to))
                .subscribeOn(Schedulers.boundedElastic())
                .map(list -> ResponseEntity.ok(Flux.fromIterable(list)));
    }
}
