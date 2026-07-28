package work.zing.ergpower.pm5.source;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import reactor.core.publisher.Flux;

import work.zing.ergpower.pm5.FrameCodec;
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

    public ReplayPm5Source(Path capture) {
        this.capture = capture;
    }

    @Override
    public Flux<Pm5Event> events() {
        return Flux.defer(() -> {
            try {
                return Flux.fromIterable(decodeAll());
            } catch (IOException e) {
                return Flux.error(e);
            }
        });
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
