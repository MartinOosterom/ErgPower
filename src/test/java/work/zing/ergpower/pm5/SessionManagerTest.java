package work.zing.ergpower.pm5;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import work.zing.ergpower.pm5.capture.SessionManager;
import work.zing.ergpower.pm5.event.GeneralStatus;
import work.zing.ergpower.pm5.source.BlePm5Source;

/**
 * Deterministic check of auto start/stop: feeding {@link SessionManager} a Workout-State sequence
 * opens and finalises session folders correctly — no hardware, no Bluetooth.
 */
class SessionManagerTest {

    @Test
    void opensAndFinalisesSessionsFromWorkoutState(@TempDir Path base) throws IOException {
        BlePm5Source source = new BlePm5Source(Path.of("ble-bridge"), null); // never started
        SessionManager manager = new SessionManager(base, source, "test");

        manager.onEvent(status(0.0, 0));   // WAITTOBEGIN -> nothing
        assertEquals(0, sessionCount(base), "no session while idle");

        manager.onEvent(status(1.0, 1));   // WORKOUTROW -> session 1 opens
        manager.onEvent(status(2.0, 1));
        assertEquals(1, sessionCount(base));

        manager.onEvent(status(3.0, 10));  // WORKOUTEND -> finalise
        Path first = sessions(base).findFirst().orElseThrow();
        assertTrue(Files.exists(first.resolve("session.json")), "manifest written on end");
        assertTrue(Files.exists(first.resolve("summary.json")), "summary written on end");

        manager.onEvent(status(4.0, 3));   // INTERVALREST (active) -> session 2 opens
        manager.onEvent(status(5.0, 1));
        manager.onEvent(status(6.0, 0));   // back to WAITTOBEGIN -> finalise
        assertEquals(2, sessionCount(base), "a second piece gets its own folder");

        manager.close();
    }

    private static GeneralStatus status(double t, int workoutState) {
        return new GeneralStatus(
                t, Instant.EPOCH.plusMillis((long) (t * 1000)),
                t * 3.0, 0, 0, workoutState, 1, 2, 0, 0, 0, 100);
    }

    private static Stream<Path> sessions(Path base) throws IOException {
        return Files.list(base)
                .filter(Files::isDirectory)
                .filter(p -> p.getFileName().toString().startsWith("session-"));
    }

    private static long sessionCount(Path base) throws IOException {
        try (Stream<Path> s = sessions(base)) {
            return s.count();
        }
    }
}
