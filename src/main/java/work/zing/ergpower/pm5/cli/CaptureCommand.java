package work.zing.ergpower.pm5.cli;

import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import work.zing.ergpower.pm5.api.LiveState;
import work.zing.ergpower.pm5.capture.CaptureService;
import work.zing.ergpower.pm5.capture.SessionManager;
import work.zing.ergpower.pm5.config.ErgPowerBleProperties;
import work.zing.ergpower.pm5.source.BlePm5Source;
import work.zing.ergpower.pm5.source.ReplayPm5Source;
import work.zing.ergpower.pm5.storage.SessionMeta;
import work.zing.ergpower.pm5.storage.SessionStorage;

/**
 * The ErgPower command line. Turns the app into a small CLI:
 *
 * <pre>{@code
 * java -jar ErgPower.jar capture [--seconds=120]     # live-capture a rowing session to a folder
 * java -jar ErgPower.jar replay <capture.ndjson>     # decode a saved capture into a session folder
 * }</pre>
 *
 * With no recognised command it prints usage and exits (so an app context that just loads — e.g. in
 * tests — does nothing). All connection/storage settings come from {@link ErgPowerBleProperties}.
 */
@Component
public class CaptureCommand implements ApplicationRunner {

    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss").withZone(ZoneId.systemDefault());
    private static final String APP_VERSION = "0.0.1-SNAPSHOT";

    private final ErgPowerBleProperties props;
    private final LiveState liveState;

    public CaptureCommand(ErgPowerBleProperties props, LiveState liveState) {
        this.props = props;
        this.liveState = liveState;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        List<String> commands = args.getNonOptionArgs();
        if (commands.contains("serve")) {
            serve();
        } else if (commands.contains("capture")) {
            capture(args);
        } else if (commands.contains("replay")) {
            replay(commands);
        } else {
            usage();
        }
    }

    /**
     * Run as a service: connect to the PM5, attach the live-API subscriber ({@link LiveState}) and the
     * storage subscriber ({@link SessionManager}) to the one multicast source, then return — the
     * reactive web server keeps the process alive serving {@code /api/v1} (design D5/D10).
     */
    private void serve() throws Exception {
        String device = props.resolvedDeviceName();
        Path storageDir = Path.of(props.storage().dir());
        BlePm5Source source =
                new BlePm5Source(Path.of(props.bridge().dir()), device, props.bridge().uvCommand());
        source.setSampleRateMillis((int) props.capture().sampleRate().toMillis());
        source.setAutoReconnect(props.connect().autoReconnect());
        source.setProfileOverride(props.resolvedProfileOverride());

        liveState.bind(source);                       // subscriber: live API (state + SSE)

        SessionManager manager = new SessionManager(storageDir, source, APP_VERSION);
        source.setRawFrameListener(manager::onRawFrame);
        source.events().subscribe(manager::onEvent);  // subscriber: storage (independent)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            source.stop();
            manager.close();
        }, "serve-shutdown"));

        source.start();
        System.err.println("ErgPower serving on http://localhost:8080/api/v1"
                + "  (rowing is auto-recorded to " + storageDir.toAbsolutePath() + "/session-*)");
    }

    private void capture(ApplicationArguments args) throws Exception {
        Duration limit = null;
        List<String> seconds = args.getOptionValues("seconds");
        if (seconds != null && !seconds.isEmpty()) {
            limit = Duration.ofMillis((long) (Double.parseDouble(seconds.getFirst()) * 1000));
        }

        String device = props.resolvedDeviceName();
        Path storageDir = Path.of(props.storage().dir());
        BlePm5Source source =
                new BlePm5Source(Path.of(props.bridge().dir()), device, props.bridge().uvCommand());
        source.setSampleRateMillis((int) props.capture().sampleRate().toMillis());
        source.setAutoReconnect(props.connect().autoReconnect());
        source.setProfileOverride(props.resolvedProfileOverride());

        String target = device != null ? " [device=" + device + "]" : " [first PM5 found]";
        if (props.capture().autoSession()) {
            System.err.println("Auto-session capture (start/stop from PM5 workout state) -> "
                    + storageDir.toAbsolutePath() + "/session-*" + target);
            CaptureService.runAuto(source, storageDir, APP_VERSION, limit);
        } else {
            Path session = storageDir.resolve("live-" + TS.format(ZonedDateTime.now()));
            System.err.println("Live capture -> " + session.toAbsolutePath()
                    + (limit != null ? " for " + limit.toSeconds() + "s" : " until disconnect/Ctrl-C") + target);
            CaptureService.run(source, session, new SessionMeta("ble", device, null, APP_VERSION), limit);
        }
    }

    private void replay(List<String> commands) throws Exception {
        int idx = commands.indexOf("replay");
        if (idx + 1 >= commands.size()) {
            System.err.println("replay needs a capture file: replay <capture.ndjson>");
            return;
        }
        Path capture = Path.of(commands.get(idx + 1));
        String name = capture.getFileName().toString().replaceFirst("\\.ndjson$", "");
        Path session = Path.of(props.storage().dir()).resolve("replay-" + name);
        SessionStorage.store(new ReplayPm5Source(capture), session,
                SessionMeta.of("replay:" + capture.getFileName()));
        System.err.println("Session written -> " + session.toAbsolutePath());
    }

    private void usage() {
        System.err.println("""
                ErgPower — Concept2 PM5 capture
                  serve                    run the live REST + SSE API on :8080/api/v1 (also records)
                  capture [--seconds=N]    live-capture a rowing session (default: until disconnect/Ctrl-C)
                  replay <capture.ndjson>  decode a saved capture into a session folder
                Config: ergpower.ble.* (device.match, device.name, bridge.dir, storage.dir, ...)""");
    }
}
