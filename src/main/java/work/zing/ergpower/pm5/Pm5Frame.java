package work.zing.ergpower.pm5;

import java.time.Instant;

/**
 * A raw BLE notification frame as forwarded by the bridge (or replayed from a capture): the source
 * characteristic, a host receive timestamp, and the undecoded payload bytes. The bridge does no
 * interpretation — decoding happens in {@link work.zing.ergpower.pm5.decode.Pm5Decoder}.
 *
 * @param hostTime         host wall-clock time at receipt
 * @param mono             monotonic seconds since bridge start (for inter-frame timing); 0 if absent
 * @param characteristicId 16-bit C2 characteristic id (e.g. 0x31)
 * @param data             raw notification bytes
 */
public record Pm5Frame(Instant hostTime, double mono, int characteristicId, byte[] data) {

    public Pm5Frame {
        data = data.clone();
    }

    @Override
    public byte[] data() {
        return data.clone();
    }

    /**
     * Extract the 16-bit characteristic id from a full C2 UUID
     * ({@code ce06XXXX-43e5-11e4-916c-0800200c9a66} → {@code 0xXXXX}).
     */
    public static int shortIdFromUuid(String uuid) {
        return Integer.parseInt(uuid.substring(4, 8), 16);
    }
}
