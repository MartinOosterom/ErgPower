package work.zing.ergpower.pm5.capture;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import reactor.core.publisher.Flux;

import work.zing.ergpower.pm5.event.Pm5Event;
import work.zing.ergpower.pm5.source.BlePm5Source;
import work.zing.ergpower.pm5.storage.SessionMeta;
import work.zing.ergpower.pm5.storage.SessionStorageWriter;

/**
 * Runs a live capture: starts a {@link BlePm5Source}, streams every event into a session folder via
 * {@link SessionStorageWriter}, and finalises the manifest/summary — once, whether the stream ends
 * normally (duration/disconnect) or the JVM shuts down (Ctrl-C). Shared by the CLI runner and the
 * standalone {@code LiveCapture} tool.
 */
public final class CaptureService {

    private CaptureService() {
    }

    /**
     * @param limit stop after this duration, or {@code null} to record until disconnect/shutdown
     * @return the session folder written
     */
    public static Path run(BlePm5Source source, Path sessionDir, SessionMeta meta, Duration limit)
            throws IOException {

        SessionStorageWriter writer = new SessionStorageWriter(sessionDir, meta);

        // Persist every raw bridge frame so this live session can be re-decoded later.
        source.setRawFrameListener(line -> {
            try {
                writer.writeRaw(line);
            } catch (IOException e) {
                System.err.println("[raw] failed to persist frame: " + e.getMessage());
            }
        });

        AtomicBoolean finalised = new AtomicBoolean(false);
        Runnable finish = () -> {
            if (finalised.compareAndSet(false, true)) {
                try {
                    writer.setDevice(source.connectedDevice()); // provenance from the bridge
                    writer.setDecodeProvenance(source.connectedFirmware(), source.activeProfileId(), source.fingerprintNote());
                    writer.finish();
                    System.err.println("Session written -> " + sessionDir.toAbsolutePath());
                } catch (IOException e) {
                    System.err.println("Failed to finalise session: " + e.getMessage());
                }
            }
        };

        Thread hook = new Thread(() -> {
            source.stop();
            finish.run();
        }, "capture-shutdown");
        Runtime.getRuntime().addShutdownHook(hook);

        source.start();
        Flux<Pm5Event> stream = source.events();
        if (limit != null) {
            stream = stream.take(limit);
        }
        stream.doOnNext(event -> writeUnchecked(writer, event)).blockLast();

        source.stop();
        finish.run();
        try {
            Runtime.getRuntime().removeShutdownHook(hook);
        } catch (IllegalStateException shuttingDown) {
            // already in shutdown; hook will have run
        }
        return sessionDir;
    }

    /**
     * Auto-session capture: sessions start/stop from the PM5 workout state (via {@link SessionManager}),
     * each written to its own folder under {@code baseDir}. Runs until the stream ends (disconnect +
     * no-reconnect), an optional {@code limit} elapses, or the JVM shuts down.
     */
    public static void runAuto(BlePm5Source source, Path baseDir, String appVersion, Duration limit)
            throws IOException {
        SessionManager manager = new SessionManager(baseDir, source, appVersion);
        source.setRawFrameListener(manager::onRawFrame);

        Thread hook = new Thread(() -> {
            source.stop();
            manager.close();
        }, "capture-shutdown");
        Runtime.getRuntime().addShutdownHook(hook);

        source.start();
        Flux<Pm5Event> stream = source.events();
        if (limit != null) {
            stream = stream.take(limit);
        }
        stream.doOnNext(manager::onEvent).blockLast();

        source.stop();
        manager.close();
        try {
            Runtime.getRuntime().removeShutdownHook(hook);
        } catch (IllegalStateException shuttingDown) {
            // already shutting down; hook will run
        }
    }

    private static void writeUnchecked(SessionStorageWriter writer, Pm5Event event) {
        try {
            writer.write(event);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
