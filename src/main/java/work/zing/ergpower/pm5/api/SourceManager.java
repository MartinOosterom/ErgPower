package work.zing.ergpower.pm5.api;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

import reactor.core.Disposable;

import work.zing.ergpower.api.model.ConnectionState;
import work.zing.ergpower.api.model.ConnectionStatus;
import work.zing.ergpower.api.model.DeviceInfo;
import work.zing.ergpower.api.model.DiscoveredDevice;
import work.zing.ergpower.api.model.SourceStatus;
import work.zing.ergpower.api.model.SourceType;
import work.zing.ergpower.pm5.capture.SessionManager;
import work.zing.ergpower.pm5.config.ErgPowerBleProperties;
import work.zing.ergpower.pm5.source.BlePm5Source;
import work.zing.ergpower.pm5.source.BridgeBinary;
import work.zing.ergpower.pm5.source.ReplayPm5Source;

/**
 * Owns the single source feeding the live pipeline ({@link LiveState}). Starting a source stops the
 * previous one, resets live state, and rewires the connection status. A {@code ble} source also gets a
 * {@link SessionManager} storage subscriber (rowing is recorded); a {@code replay} source is
 * live-only, paced by the captured timing. This is the seam that lets the browser pick between a live
 * PM5 and a stored-session replay through {@code POST /source}.
 */
@Component
public class SourceManager {

    private static final String APP_VERSION = "0.0.1-SNAPSHOT";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ErgPowerBleProperties props;
    private final LiveState liveState;
    private final SessionCatalog catalog;

    private final List<Disposable> subscriptions = new ArrayList<>();
    private SourceType sourceType = SourceType.NONE;
    private String replaySessionId;
    private BlePm5Source ble;
    private SessionManager storage;

    public SourceManager(ErgPowerBleProperties props, LiveState liveState, SessionCatalog catalog) {
        this.props = props;
        this.liveState = liveState;
        this.catalog = catalog;
    }

    public synchronized SourceStatus status() {
        return new SourceStatus().sourceType(sourceType).connection(liveState.connectionStatus())
                .sessionId(replaySessionId);
    }

    /** Connect to a live PM5 (optional device-name override) and start recording. */
    public synchronized SourceStatus startBle(String device) {
        stop();
        String name = device != null && !device.isBlank() ? device : props.resolvedDeviceName();
        BlePm5Source src = new BlePm5Source(BridgeBinary.resolve(props.bridge().binary()), name);
        src.setSampleRateMillis((int) props.capture().sampleRate().toMillis());
        src.setAutoReconnect(props.connect().autoReconnect());
        src.setProfileOverride(props.resolvedProfileOverride());

        liveState.reset();
        liveState.setConnectionProvider(() -> bleStatus(src));

        SessionManager mgr = new SessionManager(Path.of(props.storage().dir()), src, APP_VERSION);
        src.setRawFrameListener(mgr::onRawFrame);
        subscriptions.add(src.events().subscribe(liveState::onEvent)); // live API subscriber
        subscriptions.add(src.events().subscribe(mgr::onEvent));        // storage subscriber (independent)

        try {
            src.start();
        } catch (IOException e) {
            stop();
            throw new IllegalStateException("failed to start BLE bridge: " + e.getMessage(), e);
        }
        this.ble = src;
        this.storage = mgr;
        this.sourceType = SourceType.BLE;
        this.replaySessionId = null;
        return status();
    }

    /** Replay a stored session (timed by capture, scaled by {@code speed}, default real time). */
    public synchronized SourceStatus startReplay(String sessionId, Double speed) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("replay requires a sessionId");
        }
        Path raw = catalog.rawFrames(sessionId);
        if (raw == null) {
            throw new IllegalArgumentException("session '" + sessionId + "' is not replayable (no raw frames)");
        }
        stop();
        ReplayPm5Source src = new ReplayPm5Source(raw, speed == null ? 1.0 : speed);
        liveState.reset();
        this.sourceType = SourceType.REPLAY;
        this.replaySessionId = sessionId;
        liveState.setConnectionProvider(() -> replayStatus());
        subscriptions.add(src.events().subscribe(liveState::onEvent));
        return status();
    }

    /** Stop and dispose the active source; live state returns to disconnected/idle. */
    public synchronized SourceStatus stop() {
        subscriptions.forEach(Disposable::dispose);
        subscriptions.clear();
        if (ble != null) {
            ble.stop();
            ble = null;
        }
        if (storage != null) {
            storage.close();
            storage = null;
        }
        liveState.reset();
        liveState.setConnectionProvider(disconnected());
        sourceType = SourceType.NONE;
        replaySessionId = null;
        return status();
    }

    /** Scan for nearby PM5s via the bridge (blocking; run off the event loop). */
    public List<DiscoveredDevice> scanDevices() throws IOException {
        Path binary = BridgeBinary.resolve(props.bridge().binary());
        Process p = new ProcessBuilder(binary.toString(), "--scan").start();
        List<DiscoveredDevice> out = new ArrayList<>();
        try (BufferedReader reader = p.inputReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                try {
                    JsonNode n = MAPPER.readTree(line);
                    if (!n.hasNonNull("name") && !n.hasNonNull("address")) {
                        continue;
                    }
                    out.add(new DiscoveredDevice()
                            .name(n.hasNonNull("name") ? n.get("name").asText() : "")
                            .address(n.hasNonNull("address") ? n.get("address").asText() : "")
                            .rssi(n.hasNonNull("rssi") ? n.get("rssi").asInt() : null));
                } catch (Exception notADevice) {
                    // bridge log line on stdout — ignore
                }
            }
        }
        try {
            p.waitFor();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        return out;
    }

    private static Supplier<ConnectionStatus> disconnected() {
        return () -> new ConnectionStatus().state(ConnectionState.DISCONNECTED).since(OffsetDateTime.now());
    }

    private static ConnectionStatus replayStatus() {
        return new ConnectionStatus().state(ConnectionState.CONNECTED).since(OffsetDateTime.now());
    }

    private static ConnectionStatus bleStatus(BlePm5Source src) {
        ConnectionStatus cs = new ConnectionStatus().state(mapState(src.connectionState()))
                .firmware(src.connectedFirmware()).profileId(src.activeProfileId()).since(OffsetDateTime.now());
        if (src.connectedDevice() != null) {
            cs.device(new DeviceInfo().name(src.connectedDevice())
                    .address(src.connectedAddress() == null ? "" : src.connectedAddress()));
        }
        return cs;
    }

    private static ConnectionState mapState(String s) {
        if (s == null) {
            return ConnectionState.CONNECTING;
        }
        return switch (s.toLowerCase()) {
            case "connected" -> ConnectionState.CONNECTED;
            case "searching", "scanning" -> ConnectionState.SEARCHING;
            case "reconnecting" -> ConnectionState.RECONNECTING;
            case "disconnected" -> ConnectionState.DISCONNECTED;
            default -> ConnectionState.CONNECTING;
        };
    }
}
