package work.zing.ergpower.pm5.event;

import java.time.Instant;

/**
 * Lossless fallback for a characteristic we don't yet decode into a richer type (e.g. {@code 0x32},
 * {@code 0x33}, {@code 0x37}–{@code 0x3A}). Preserves the raw bytes so nothing is dropped before the
 * current interface definition lets us map every field. {@link #pmTime()} is decoded from the leading
 * 3-byte elapsed-time field when present, else {@code NaN}.
 *
 * @param pmTime           PM5 elapsed time (s), or {@code NaN} if the payload has no elapsed field
 * @param hostTime         host receive time
 * @param characteristicId 16-bit C2 characteristic id (e.g. 0x33)
 * @param data             raw notification bytes
 */
public record RawFrame(
        double pmTime,
        Instant hostTime,
        int characteristicId,
        byte[] data) implements Pm5Event {

    public RawFrame {
        data = data.clone();
    }

    @Override
    public byte[] data() {
        return data.clone();
    }
}
