package work.zing.ergpower.pm5.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Externalised configuration for connecting to and capturing from the PM5 (design decision D6). The
 * JVM owns this as the source of truth and passes the BLE-relevant bits to the bridge at launch; the
 * effective values are recorded into each session's manifest.
 *
 * <p>Bound from {@code ergpower.ble.*} (e.g. {@code application.yml}, env vars, or CLI overrides such
 * as {@code --ergpower.ble.device.name=...}). All fields have sensible defaults via {@link DefaultValue}.
 */
@ConfigurationProperties(prefix = "ergpower.ble")
public record ErgPowerBleProperties(
        @DefaultValue Device device,
        @DefaultValue Connect connect,
        @DefaultValue Capture capture,
        @DefaultValue Bridge bridge,
        @DefaultValue Storage storage,
        @DefaultValue Firmware firmware) {

    /** How to pick which PM5 to connect to. */
    public enum Match {
        /** Match the advertised name/serial in {@link Device#name()}. */
        NAME,
        /** Reuse a cached CoreBluetooth peripheral UUID (not yet implemented in the bridge). */
        PERIPHERAL_ID,
        /** Connect to the first PM5 found advertising the Concept2 rowing service. */
        FIRST
    }

    public record Device(
            @DefaultValue("FIRST") Match match,
            String name,
            String peripheralId) {
    }

    public record Connect(
            @DefaultValue("10s") Duration scanTimeout,
            @DefaultValue("15s") Duration connectTimeout,
            @DefaultValue("true") boolean autoReconnect) {
    }

    public record Capture(
            @DefaultValue("500ms") Duration sampleRate,
            @DefaultValue("true") boolean forceCurve,
            @DefaultValue("true") boolean autoSession) {
    }

    public record Bridge(
            @DefaultValue("ble-bridge") String dir,
            @DefaultValue("uv") String uvCommand) {
    }

    public record Storage(
            @DefaultValue("sessions") String dir) {
    }

    /** Firmware profile selection: {@code auto} (detect) or a profile id/alias (e.g. current | reference). */
    public record Firmware(
            @DefaultValue("auto") String profile) {
    }

    /** The advertised name to pass to the bridge, or {@code null} for first-found. */
    public String resolvedDeviceName() {
        return device.match() == Match.NAME ? device.name() : null;
    }

    /** The forced firmware profile id, or {@code null} when set to auto-detect. */
    public String resolvedProfileOverride() {
        return "auto".equalsIgnoreCase(firmware.profile()) ? null : firmware.profile();
    }
}
