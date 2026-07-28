package work.zing.ergpower.pm5.source;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import work.zing.ergpower.pm5.FrameCodec;
import work.zing.ergpower.pm5.Pm5Frame;
import work.zing.ergpower.pm5.decode.Pm5Decoder;
import work.zing.ergpower.pm5.event.Pm5Event;
import work.zing.ergpower.pm5.firmware.FirmwareProfile;
import work.zing.ergpower.pm5.firmware.FirmwareProfileRegistry;

/**
 * Live {@link Pm5Source} backed by the native BLE bridge binary (design decisions D1–D4).
 *
 * <p>Launches the bundled, platform-specific bridge executable (see {@link BridgeBinary}) as a child
 * process, reads its stdout NDJSON frames on a virtual thread, decodes each into typed events via
 * {@link Pm5Decoder}, and publishes them through a multicast {@link reactor.core.publisher.Sinks sink}.
 * The bridge does the BLE (cross-platform, via btleplug); all decoding stays here. stderr is drained to
 * the host log. The wire protocol is identical to the previous Python bridge, so nothing here changed
 * when the transport was reimplemented.
 *
 * <p>This supervises the process minimally: when the bridge exits (disconnect, kill, error) the stream
 * completes; the bridge itself handles BLE-level auto-reconnect with backoff.
 */
public final class BlePm5Source implements Pm5Source, AutoCloseable {

    private final Path binary;
    private final String deviceName;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Sinks.Many<Pm5Event> sink = Sinks.many().multicast().onBackpressureBuffer();
    private final FirmwareProfileRegistry registry = new FirmwareProfileRegistry();

    // Decoder is swapped once the firmware is known (the meta line arrives before any data frame).
    private volatile Pm5Decoder decoder = new Pm5Decoder();

    private volatile Process process;
    private volatile boolean stopped;
    private volatile Consumer<String> rawFrameListener;
    private volatile String connectedDevice;
    private volatile String connectedAddress;
    private volatile String connectedFirmware;
    private volatile String activeProfileId = new Pm5Decoder().profile().id();
    private volatile String fingerprintNote;
    private volatile String connectionState;
    private volatile Integer sampleRateMillis;
    private volatile boolean autoReconnect = true;
    private volatile String profileOverride;

    // Observed characteristic lengths for the fingerprint check.
    private final Map<Integer, Integer> observedLengths = new ConcurrentHashMap<>();
    private volatile boolean fingerprintChecked;

    public BlePm5Source(Path binary, String deviceName) {
        this.binary = binary;
        this.deviceName = deviceName;
    }

    /** Status sample rate to request on connect (1000/500/250/100 ms), or {@code null} for PM5 default. */
    public void setSampleRateMillis(Integer millis) {
        this.sampleRateMillis = millis;
    }

    /** Whether the bridge auto-reconnects on a PM5 drop (default true). Set before {@link #start()}. */
    public void setAutoReconnect(boolean autoReconnect) {
        this.autoReconnect = autoReconnect;
    }

    /** Force a firmware profile by id/alias ({@code current} / {@code reference} / …); {@code null}/auto = detect. */
    public void setProfileOverride(String profileOverride) {
        this.profileOverride = profileOverride;
    }

    /** Launch the bridge and begin publishing. Idempotent-ish: call once per source. */
    public void start() throws IOException {
        List<String> cmd = new ArrayList<>(List.of(binary.toString()));
        if (deviceName != null && !deviceName.isBlank()) {
            cmd.add("--name");
            cmd.add(deviceName);
        }
        if (sampleRateMillis != null) {
            cmd.add("--sample-rate-ms");
            cmd.add(String.valueOf(sampleRateMillis));
        }
        if (!autoReconnect) {
            cmd.add("--no-reconnect");
        }
        process = new ProcessBuilder(cmd).start();
        Thread.ofVirtual().name("pm5-bridge-stdout").start(this::readFrames);
        Thread.ofVirtual().name("pm5-bridge-stderr").start(this::drainStderr);
    }

    /**
     * Send a JSON command to the bridge (written to its stdin), e.g.
     * {@code {"cmd":"sample_rate","ms":250}} or {@code {"cmd":"write","uuid":"…","hex":"…"}}.
     */
    public void sendCommand(String json) {
        Process p = process;
        if (p == null) {
            return;
        }
        try {
            var writer = p.outputWriter();
            writer.write(json);
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            System.err.println("[bridge] command failed: " + e.getMessage());
        }
    }

    @Override
    public Flux<Pm5Event> events() {
        return sink.asFlux();
    }

    /**
     * Register a listener that receives every raw stdout frame line before decoding (used to persist
     * {@code raw.ndjson} so a live session can be re-decoded later). Set before {@link #start()}.
     */
    public void setRawFrameListener(Consumer<String> listener) {
        this.rawFrameListener = listener;
    }

    /** The advertised name of the PM5 the bridge actually connected to, or {@code null} if unknown. */
    public String connectedDevice() {
        return connectedDevice;
    }

    public String connectedAddress() {
        return connectedAddress;
    }

    /** The PM5 firmware revision string reported by the bridge, or {@code null} if unknown. */
    public String connectedFirmware() {
        return connectedFirmware;
    }

    /** Id of the firmware profile currently decoding this connection. */
    public String activeProfileId() {
        return activeProfileId;
    }

    /** A note if the wire fingerprint disagreed with the active profile, else {@code null}. */
    public String fingerprintNote() {
        return fingerprintNote;
    }

    private void readFrames() {
        try (BufferedReader reader = process.inputReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                if (line.indexOf("\"meta\"") >= 0) {
                    handleMeta(line);
                    continue;
                }
                Consumer<String> raw = rawFrameListener;
                if (raw != null) {
                    try {
                        raw.accept(line);
                    } catch (Exception e) {
                        System.err.println("[bridge] raw-frame listener failed: " + e.getMessage());
                    }
                }
                try {
                    Pm5Frame frame = FrameCodec.parse(line);
                    observedLengths.putIfAbsent(frame.characteristicId(), frame.data().length);
                    checkFingerprint();
                    for (Pm5Event event : decoder.decode(frame)) {
                        sink.tryEmitNext(event);
                    }
                } catch (Exception malformed) {
                    System.err.println("[bridge] dropped malformed frame: " + malformed.getMessage());
                }
            }
        } catch (IOException e) {
            if (!stopped) {
                sink.tryEmitError(e);
                return;
            }
        }
        sink.tryEmitComplete();
    }

    private void handleMeta(String line) {
        try {
            JsonNode n = MAPPER.readTree(line);
            if (n.has("state")) {
                connectionState = n.get("state").asText();
                System.err.println("[bridge] state: " + connectionState);
                return;
            }
            if (n.has("name")) {
                connectedDevice = n.get("name").asText();
                connectedAddress = n.has("address") ? n.get("address").asText() : null;
                connectedFirmware = n.hasNonNull("firmware") ? n.get("firmware").asText() : null;
                // Firmware is known before the first data frame → select the profile now.
                FirmwareProfile profile = registry.select(profileOverride, connectedFirmware);
                decoder = new Pm5Decoder(profile);
                activeProfileId = profile.id();
                fingerprintChecked = false;
                observedLengths.clear();
                System.err.println("[bridge] connected device: " + connectedDevice
                        + " @ " + connectedAddress + " | firmware=" + connectedFirmware
                        + " | profile=" + activeProfileId);
            }
        } catch (Exception ignored) {
            // not a valid meta line; ignore
        }
    }

    /** Once enough characteristics have been seen, confirm the active profile against the wire lengths. */
    private void checkFingerprint() {
        if (fingerprintChecked || observedLengths.size() < 3) {
            return;
        }
        fingerprintChecked = true;
        FirmwareProfile active = decoder.profile();
        boolean matches = observedLengths.entrySet().stream().allMatch(e -> {
            int expected = active.expectedLength(e.getKey());
            return expected < 0 || expected == e.getValue();
        });
        if (matches) {
            return;
        }
        Optional<FirmwareProfile> better = registry.byFingerprint(observedLengths);
        fingerprintNote = "wire lengths " + observedLengths + " do not match active profile "
                + active.id() + (better.isPresent() ? "; fingerprint matches " + better.get().id() : "");
        System.err.println("[bridge] WARNING: " + fingerprintNote
                + " — force it via ergpower.ble.firmware.profile, or re-decode from raw.ndjson");
    }

    /** Last connection state reported by the bridge (searching/connected/disconnected/reconnecting). */
    public String connectionState() {
        return connectionState;
    }

    private void drainStderr() {
        try (BufferedReader reader = process.errorReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.err.println("[bridge] " + line);
            }
        } catch (IOException ignored) {
            // process ended; nothing to drain
        }
    }

    /** Stop the bridge and complete the stream. */
    public void stop() {
        stopped = true;
        Process p = process;
        if (p != null) {
            p.destroy();
        }
        sink.tryEmitComplete();
    }

    @Override
    public void close() {
        stop();
    }
}
