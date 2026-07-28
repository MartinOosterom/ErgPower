package work.zing.ergpower.pm5.storage;

/**
 * Provenance recorded into a session's {@code session.json} manifest. Device/firmware may be
 * {@code null} when unknown (e.g. replay). Real captures should fill these from the bridge handshake
 * and the effective connection config.
 *
 * @param source     where the events came from, e.g. {@code "replay:<file>"} or {@code "ble:PM5 …"}
 * @param deviceName advertised PM5 name/serial, or {@code null}
 * @param firmware   PM5 firmware version, or {@code null}
 * @param appVersion ErgPower version that produced the capture
 */
public record SessionMeta(String source, String deviceName, String firmware, String appVersion) {

    public static SessionMeta of(String source) {
        return new SessionMeta(source, null, null, "0.0.1-SNAPSHOT");
    }
}
