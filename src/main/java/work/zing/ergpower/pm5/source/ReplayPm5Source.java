package work.zing.ergpower.pm5.source;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import reactor.core.publisher.Flux;

import work.zing.ergpower.pm5.FrameCodec;
import work.zing.ergpower.pm5.Pm5Frame;
import work.zing.ergpower.pm5.decode.Pm5Decoder;
import work.zing.ergpower.pm5.event.Pm5Event;

/**
 * A {@link Pm5Source} that replays raw frames captured to an NDJSON file (one frame per line:
 * {@code {"hostTime","mono","uuid","bytes"}}) back through the real {@link Pm5Decoder}. This lets the
 * whole pipeline — decode, pub/sub, storage — be built and regression-tested against real captures
 * with no Bluetooth and no PM5 (design decision D3, "raw-frame-level replay").
 *
 * <p>This first cut emits events as fast as the subscriber consumes them (order preserved); honoring
 * the original inter-frame timing via {@code mono} is a later refinement.
 */
public final class ReplayPm5Source implements Pm5Source {

    private final Path capture;
    private final Double speed; // null = as fast as possible (tests); set = timed playback

    public ReplayPm5Source(Path capture) {
        this(capture, null);
    }

    /** @param speed real-time multiplier for timed playback (1.0 = real time); {@code null} = fast. */
    public ReplayPm5Source(Path capture, Double speed) {
        this.capture = capture;
        this.speed = speed;
    }

    @Override
    public Flux<Pm5Event> events() {
        if (speed == null) {
            return Flux.defer(() -> {
                try {
                    return Flux.fromIterable(decodeAll());
                } catch (IOException e) {
                    return Flux.error(e);
                }
            });
        }
        return pacedEvents();
    }

    /** Timed playback: emit each frame's events honouring the captured inter-frame timing, scaled by speed. */
    private Flux<Pm5Event> pacedEvents() {
        double factor = speed <= 0 ? 1.0 : speed;
        return Flux.create(sink -> Thread.ofVirtual().name("replay").start(() -> {
            Pm5Decoder decoder = new Pm5Decoder();
            try (BufferedReader reader = Files.newBufferedReader(capture)) {
                String line;
                double prevMono = Double.NaN;
                while ((line = reader.readLine()) != null && !sink.isCancelled()) {
                    if (line.isBlank()) {
                        continue;
                    }
                    Pm5Frame frame = FrameCodec.parse(line);
                    if (!Double.isNaN(prevMono)) {
                        long waitMs = (long) Math.min(Math.max((frame.mono() - prevMono) / factor * 1000.0, 0), 5000);
                        if (waitMs > 0) {
                            Thread.sleep(waitMs);
                        }
                    }
                    prevMono = frame.mono();
                    for (Pm5Event event : decoder.decode(frame)) {
                        sink.next(event);
                    }
                }
                sink.complete();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                sink.error(e);
            }
        }));
    }

    private List<Pm5Event> decodeAll() throws IOException {
        List<Pm5Event> out = new ArrayList<>();
        Pm5Decoder decoder = new Pm5Decoder();
        try (BufferedReader reader = Files.newBufferedReader(capture)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                out.addAll(decoder.decode(FrameCodec.parse(line)));
            }
        }
        return out;
    }
}
