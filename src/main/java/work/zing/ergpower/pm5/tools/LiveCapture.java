package work.zing.ergpower.pm5.tools;

import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import work.zing.ergpower.pm5.capture.CaptureService;
import work.zing.ergpower.pm5.source.BlePm5Source;
import work.zing.ergpower.pm5.source.BridgeBinary;
import work.zing.ergpower.pm5.storage.SessionMeta;

/**
 * Standalone live capture (no Spring): connect to the PM5 through the native bridge binary and stream a
 * session straight to disk. Equivalent to the {@code capture} CLI command; handy when running from raw
 * classpath rather than the packaged jar.
 *
 * <pre>{@code
 * java -cp <cp> work.zing.ergpower.pm5.tools.LiveCapture [outputDir] [--name "PM5 …"] [--seconds N]
 * }</pre>
 */
public final class LiveCapture {

    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss").withZone(ZoneId.systemDefault());

    private LiveCapture() {
    }

    public static void main(String[] args) throws Exception {
        String outputDir = "sessions";
        String name = null;
        double seconds = 0;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--name" -> name = args[++i];
                case "--seconds" -> seconds = Double.parseDouble(args[++i]);
                default -> outputDir = args[i];
            }
        }

        Path session = Path.of(outputDir).resolve("live-" + TS.format(ZonedDateTime.now()));
        Duration limit = seconds > 0 ? Duration.ofMillis((long) (seconds * 1000)) : null;

        System.err.println("Starting live capture -> " + session.toAbsolutePath()
                + (limit != null ? " for " + seconds + "s" : " until disconnect/Ctrl-C"));
        CaptureService.run(
                new BlePm5Source(BridgeBinary.resolve(null), name),
                session,
                new SessionMeta("ble", name, null, "0.0.1-SNAPSHOT"),
                limit);
    }
}
