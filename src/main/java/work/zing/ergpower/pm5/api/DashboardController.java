package work.zing.ergpower.pm5.api;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import work.zing.ergpower.api.DashboardsApi;
import work.zing.ergpower.api.model.Dashboard;
import work.zing.ergpower.api.model.DashboardSummary;

/**
 * Implements the generated {@link DashboardsApi}: CRUD over dashboard profiles persisted server-side as
 * JSON by {@link DashboardStore}. Blocking file I/O runs off the event loop; an unsafe name surfaces as
 * 400 and a missing profile as 404 (RFC 7807 problems).
 */
@RestController
public class DashboardController implements DashboardsApi {

    private final DashboardStore store;

    public DashboardController(DashboardStore store) {
        this.store = store;
    }

    @Override
    public Mono<ResponseEntity<Flux<DashboardSummary>>> listDashboards(ServerWebExchange exchange) {
        return Mono.fromCallable(store::list)
                .subscribeOn(Schedulers.boundedElastic())
                .map(names -> ResponseEntity.ok(Flux.fromIterable(names.stream().map(DashboardSummary::new).toList())));
    }

    @Override
    public Mono<ResponseEntity<Dashboard>> getDashboard(String name, ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            Map<String, Object> config = store.read(name);
            if (config == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no such dashboard: " + name);
            }
            return ResponseEntity.ok(new Dashboard(name, config));
        }).subscribeOn(Schedulers.boundedElastic()).onErrorMap(this::badName);
    }

    @Override
    public Mono<ResponseEntity<Dashboard>> putDashboard(String name, Mono<Map<String, Object>> requestBody,
            ServerWebExchange exchange) {
        return requestBody
                .flatMap(config -> Mono.fromCallable(() -> {
                    store.write(name, config);
                    return ResponseEntity.ok(new Dashboard(name, config));
                }).subscribeOn(Schedulers.boundedElastic()))
                .onErrorMap(this::badName);
    }

    @Override
    public Mono<ResponseEntity<Void>> deleteDashboard(String name, ServerWebExchange exchange) {
        return Mono.fromCallable(() -> store.delete(name))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(existed -> existed
                        ? Mono.just(ResponseEntity.noContent().<Void>build())
                        : Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "no such dashboard: " + name)))
                .onErrorMap(this::badName);
    }

    /** Map an unsafe-name IllegalArgumentException to a 400; leave everything else untouched. */
    private Throwable badName(Throwable t) {
        return t instanceof IllegalArgumentException
                ? new ResponseStatusException(HttpStatus.BAD_REQUEST, t.getMessage())
                : t;
    }
}
