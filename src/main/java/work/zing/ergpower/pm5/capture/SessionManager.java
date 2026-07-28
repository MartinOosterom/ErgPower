package work.zing.ergpower.pm5.capture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import work.zing.ergpower.pm5.event.GeneralStatus;
import work.zing.ergpower.pm5.event.Pm5Event;
import work.zing.ergpower.pm5.source.BlePm5Source;
import work.zing.ergpower.pm5.storage.SessionMeta;
import work.zing.ergpower.pm5.storage.SessionStorageWriter;

/**
 * Auto-captures rowing sessions by watching the PM5 <b>Workout State</b> ({@code 0x0031}): a new
 * session folder is opened when a piece starts and finalised when it ends, so multiple pieces in one
 * run each get their own folder — no {@code --seconds}, no Ctrl-C needed (design task 5.5).
 *
 * <p>Workout-state enum (rev 1.30): {@code 0=WAITTOBEGIN}, {@code 1..9} active
 * (row / countdown / interval work+rest), {@code 10=WORKOUTEND}, {@code 11=TERMINATE},
 * {@code 12=WORKOUTLOGGED}. A session is active while the state is 1..9.
 *
 * <p>Fed on a single thread (the bridge reader) via {@link #onEvent}/{@link #onRawFrame}; methods are
 * {@code synchronized} so the shutdown hook can safely finalise from another thread.
 */
public final class SessionManager implements AutoCloseable {

    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss").withZone(ZoneId.systemDefault());

    private final Path baseDir;
    private final BlePm5Source source;
    private final String appVersion;

    private SessionStorageWriter active;
    private Path activeDir;

    public SessionManager(Path baseDir, BlePm5Source source, String appVersion) {
        this.baseDir = baseDir;
        this.source = source;
        this.appVersion = appVersion;
    }

    /** Route a raw bridge frame to the active session's {@code raw.ndjson}, if a session is open. */
    public synchronized void onRawFrame(String line) {
        if (active == null) {
            return;
        }
        try {
            active.writeRaw(line);
        } catch (IOException e) {
            System.err.println("[session] raw write failed: " + e.getMessage());
        }
    }

    /** Update session lifecycle from workout state, then persist the event if a session is open. */
    public synchronized void onEvent(Pm5Event event) {
        if (event instanceof GeneralStatus g) {
            handleState(g.workoutState());
        }
        if (active != null) {
            try {
                active.write(event);
            } catch (IOException e) {
                System.err.println("[session] write failed: " + e.getMessage());
            }
        }
    }

    private void handleState(int workoutState) {
        boolean workoutActive = workoutState >= 1 && workoutState <= 9;
        if (workoutActive && active == null) {
            startSession();
        } else if (!workoutActive && active != null) {
            endSession();
        }
    }

    private void startSession() {
        String name = "session-" + TS.format(ZonedDateTime.now());
        Path dir = baseDir.resolve(name);
        for (int n = 2; Files.exists(dir); n++) { // avoid collisions for rapid pieces
            dir = baseDir.resolve(name + "-" + n);
        }
        activeDir = dir;
        try {
            active = new SessionStorageWriter(
                    activeDir, new SessionMeta("ble", source.connectedDevice(), null, appVersion));
            System.err.println("Session started -> " + activeDir.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("[session] failed to start: " + e.getMessage());
            active = null;
            activeDir = null;
        }
    }

    private void endSession() {
        try {
            active.setDevice(source.connectedDevice());
            active.setDecodeProvenance(source.connectedFirmware(), source.activeProfileId(), source.fingerprintNote());
            active.finish();
            System.err.println("Session ended -> " + activeDir.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("[session] failed to finalise: " + e.getMessage());
        } finally {
            active = null;
            activeDir = null;
        }
    }

    /** Finalise any in-progress session (e.g. on shutdown / stream end). */
    @Override
    public synchronized void close() {
        if (active != null) {
            endSession();
        }
    }
}
