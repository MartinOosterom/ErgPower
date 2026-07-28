package work.zing.ergpower.pm5.firmware;

import java.time.Instant;

import work.zing.ergpower.pm5.event.AdditionalStatus2;
import work.zing.ergpower.pm5.event.Pm5Event;
import work.zing.ergpower.pm5.event.StrokeData;

/**
 * Profile for the current PM5 firmware observed on hardware (PM5 432234859, 2026). It diverges from
 * rev 1.30 in exactly two characteristics for the fields we decode:
 * <ul>
 *   <li>{@code 0x0033} is 20 bytes with a 2-byte field inserted at [6:8], shifting the split fields +2;</li>
 *   <li>{@code 0x0035} is 20 bytes with the stroke count moved to [18:20].</li>
 * </ul>
 * Everything else ({@code 0x31/0x32/0x36/0x37/0x38}, force curve) matches the base — the 15-byte
 * {@code 0x36} and 19-byte {@code 0x38} share the base offsets for the fields we expose.
 */
public final class CurrentPm5 extends FirmwareProfile {

    @Override
    public String id() {
        return "current-pm5-2026";
    }

    @Override
    public int expectedLength(int characteristicId) {
        return switch (characteristicId) {
            case 0x31 -> 19;
            case 0x32 -> 17;
            case 0x33 -> 20;
            case 0x35 -> 20;
            case 0x36 -> 15;
            case 0x37 -> 18;
            case 0x38 -> 19;
            default -> -1;
        };
    }

    /** {@code 0x0033}: split fields shifted +2 (avg pace at [8:10], avg power at [10:12]). */
    @Override
    public Pm5Event additionalStatus2(byte[] b, Instant host) {
        if (b.length < 12) {
            return raw(0x33, b, host);
        }
        return new AdditionalStatus2(
                u24(b, 0) / 100.0, host,
                u8(b, 3), u16(b, 4),
                u16(b, 8) / 100.0, u16(b, 10));
    }

    /** {@code 0x0035}: 20-byte frame with stroke count at [18:20]. */
    @Override
    public Pm5Event strokeData(byte[] b, Instant host) {
        if (b.length < 20) {
            return raw(0x35, b, host);
        }
        return new StrokeData(
                u24(b, 0) / 100.0, host,
                u24(b, 3) / 10.0,
                u8(b, 6) / 100.0, u8(b, 7) / 100.0,
                u16(b, 8) / 100.0, u16(b, 10) / 100.0,
                u16(b, 12) / 10.0 * LBF_TO_NEWTON,
                u16(b, 14) / 10.0 * LBF_TO_NEWTON,
                u16(b, 18));
    }
}
