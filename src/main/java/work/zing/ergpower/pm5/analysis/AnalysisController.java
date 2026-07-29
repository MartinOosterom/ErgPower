package work.zing.ergpower.pm5.analysis;

import java.util.NoSuchElementException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import work.zing.ergpower.api.AnalysisApi;
import work.zing.ergpower.api.model.SessionAnalysis;

/**
 * Implements the generated {@link AnalysisApi}: the deterministic technique analysis of a stored
 * session. A read — file I/O + feature extraction run off the event loop; an unknown session is a 404.
 */
@RestController
public class AnalysisController implements AnalysisApi {

    private final TechniqueAnalyzer analyzer;

    public AnalysisController(TechniqueAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

    @Override
    public Mono<ResponseEntity<SessionAnalysis>> getSessionAnalysis(String id, ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            try {
                return analyzer.analyze(id);
            } catch (NoSuchElementException e) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no such session: " + id);
            }
        }).subscribeOn(Schedulers.boundedElastic()).map(ResponseEntity::ok);
    }
}
