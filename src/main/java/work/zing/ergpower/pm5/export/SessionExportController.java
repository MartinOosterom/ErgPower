package work.zing.ergpower.pm5.export;

import java.util.NoSuchElementException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Serves a stored session as a downloadable .FIT file. Hand-written (binary streaming) like the SSE
 * controller; the {@code /api/v1} prefix is applied by {@code ApiRoutingConfig}. Encoding + file I/O
 * run off the event loop; an unknown session is a 404.
 */
@RestController
public class SessionExportController {

    private static final MediaType FIT = MediaType.parseMediaType("application/vnd.ant.fit");

    private final SessionFitExporter exporter;

    public SessionExportController(SessionFitExporter exporter) {
        this.exporter = exporter;
    }

    @GetMapping(value = "/sessions/{id}/export.fit", produces = "application/vnd.ant.fit")
    public Mono<ResponseEntity<byte[]>> exportFit(@PathVariable String id) {
        return Mono.fromCallable(() -> {
            try {
                return exporter.export(id);
            } catch (NoSuchElementException e) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no such session: " + id);
            }
        }).subscribeOn(Schedulers.boundedElastic()).map(bytes -> ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + safe(id) + ".fit\"")
                .contentType(FIT)
                .body(bytes));
    }

    private static String safe(String id) {
        return id.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
