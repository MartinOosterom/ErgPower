package work.zing.ergpower.pm5.api;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

import work.zing.ergpower.api.model.SessionSummary;
import work.zing.ergpower.pm5.config.ErgPowerBleProperties;

/**
 * Lists stored sessions from the storage directory (the folder IS the store), reading each session's
 * {@code session.json} + {@code summary.json}. A session is replayable if it kept its raw frames.
 */
@Component
public class SessionCatalog {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path storageDir;

    public SessionCatalog(ErgPowerBleProperties props) {
        this.storageDir = Path.of(props.storage().dir());
    }

    /** Newest first. */
    public List<SessionSummary> list() {
        if (!Files.isDirectory(storageDir)) {
            return List.of();
        }
        try (Stream<Path> dirs = Files.list(storageDir)) {
            return dirs.filter(Files::isDirectory)
                    .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
                    .map(this::toSummary)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /** The raw-frame file for a replayable session, or {@code null} if it can't be replayed. */
    public Path rawFrames(String sessionId) {
        Path raw = storageDir.resolve(sessionId).resolve("raw.ndjson");
        return Files.exists(raw) ? raw : null;
    }

    private SessionSummary toSummary(Path dir) {
        String id = dir.getFileName().toString();
        SessionSummary s = new SessionSummary().id(id).replayable(Files.exists(dir.resolve("raw.ndjson")));
        JsonNode m = readJson(dir.resolve("session.json"));
        if (m != null) {
            s.startedAt(text(m, "startedAt")).device(text(m, "device")).firmware(text(m, "firmware"));
        }
        JsonNode sum = readJson(dir.resolve("summary.json"));
        if (sum != null) {
            s.strokes(intOrNull(sum, "strokes")).distanceM(decOrNull(sum, "distanceM"))
             .durationS(decOrNull(sum, "durationS")).avgPowerW(intOrNull(sum, "avgPowerW"))
             .peakPowerW(intOrNull(sum, "peakPowerW"));
        }
        return s;
    }

    private static JsonNode readJson(Path p) {
        try {
            return Files.exists(p) ? MAPPER.readTree(Files.readString(p)) : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static String text(JsonNode n, String field) {
        return n.hasNonNull(field) ? n.get(field).asText() : null;
    }

    private static Integer intOrNull(JsonNode n, String field) {
        return n.hasNonNull(field) ? n.get(field).asInt() : null;
    }

    private static BigDecimal decOrNull(JsonNode n, String field) {
        return n.hasNonNull(field) ? BigDecimal.valueOf(n.get(field).asDouble()) : null;
    }
}
