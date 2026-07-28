package work.zing.ergpower.pm5.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

import work.zing.ergpower.api.ConnectionApi;
import work.zing.ergpower.api.model.ConnectionStatus;

/** Implements the generated {@link ConnectionApi} — a signature drift from the spec fails compilation. */
@RestController
public class ConnectionController implements ConnectionApi {

    private final LiveState liveState;

    public ConnectionController(LiveState liveState) {
        this.liveState = liveState;
    }

    @Override
    public Mono<ResponseEntity<ConnectionStatus>> getConnection(ServerWebExchange exchange) {
        return Mono.just(ResponseEntity.ok(liveState.connectionStatus()));
    }
}
