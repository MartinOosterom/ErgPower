package work.zing.ergpower.pm5.storage;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import work.zing.ergpower.pm5.event.Pm5Event;
import work.zing.ergpower.pm5.source.Pm5Source;

/**
 * Drives a {@link Pm5Source} into a session folder on disk. This is the "capture → store" glue: it
 * subscribes to the source, writes every event via {@link SessionStorageWriter}, and finalises the
 * manifest + summary.
 *
 * <p>Session lifecycle here is the whole stream (one source = one session), which suits replay and a
 * single "Just Row". Deriving multiple sessions from PM5 workout-state transitions is task 5.5.
 */
public final class SessionStorage {

    private static final DateTimeFormatter FOLDER_TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss").withZone(ZoneId.systemDefault());

    private SessionStorage() {
    }

    /** Consume the source into {@code sessionDir}, returning the folder written. */
    public static Path store(Pm5Source source, Path sessionDir, SessionMeta meta) throws IOException {
        try (SessionStorageWriter writer = new SessionStorageWriter(sessionDir, meta)) {
            for (Pm5Event event : source.events().toIterable()) {
                writer.write(event);
            }
            writer.finish();
        }
        return sessionDir;
    }

    /** Conventional folder name: {@code <startTime>__<distance>m}. */
    public static String folderName(Instant start, double distanceMeters) {
        return FOLDER_TS.format(start) + "__" + Math.round(distanceMeters) + "m";
    }
}
