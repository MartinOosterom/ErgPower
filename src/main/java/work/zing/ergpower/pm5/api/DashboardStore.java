package work.zing.ergpower.pm5.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Server-side persistence of dashboard profiles: one JSON file per profile at
 * {@code <dir>/<name>.json} (dir configurable via {@code ergpower.dashboards.dir}, default
 * {@code dashboards/}). The stored file <em>is</em> the profile's config, kept and returned
 * <b>opaquely</b> as a JSON object — the server never interprets the widget internals, so the browser's
 * widget model can evolve without any change here.
 *
 * <p>Because a profile name maps to a filename, {@link #fileFor} rejects anything that could escape the
 * dashboards directory (path separators, {@code ..}); callers surface that as a 400.
 */
@Component
public class DashboardStore {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() {};

    private final Path dir;

    public DashboardStore(@Value("${ergpower.dashboards.dir:dashboards}") String dir) {
        this.dir = Path.of(dir);
    }

    /** Stored profile names (sorted), or empty if none. */
    public List<String> list() {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".json"))
                    .map(n -> n.substring(0, n.length() - ".json".length()))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /** The profile's opaque config, or {@code null} if it doesn't exist. */
    public Map<String, Object> read(String name) throws IOException {
        Path file = fileFor(name);
        return Files.exists(file) ? MAPPER.readValue(Files.readString(file), JSON_OBJECT) : null;
    }

    /** Create or replace the profile with the given opaque config. */
    public void write(String name, Map<String, Object> config) throws IOException {
        Path file = fileFor(name);
        Files.createDirectories(dir);
        Files.writeString(file, MAPPER.writerWithDefaultPrettyPrinter()
                .writeValueAsString(config == null ? Map.of() : config));
    }

    /** Delete the profile; returns whether it existed. */
    public boolean delete(String name) throws IOException {
        return Files.deleteIfExists(fileFor(name));
    }

    /** Resolve {@code <dir>/<name>.json}, rejecting names that aren't a safe single filename. */
    Path fileFor(String name) {
        if (name == null || name.isBlank() || name.length() > 100
                || name.contains("/") || name.contains("\\") || name.contains("..")
                || name.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("invalid profile name: " + name);
        }
        Path file = dir.resolve(name + ".json").normalize();
        if (!dir.normalize().equals(file.getParent())) {
            throw new IllegalArgumentException("invalid profile name: " + name);
        }
        return file;
    }
}
