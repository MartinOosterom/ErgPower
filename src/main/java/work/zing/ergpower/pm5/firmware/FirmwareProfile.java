package work.zing.ergpower.pm5.firmware;

import java.time.Instant;

import work.zing.ergpower.pm5.event.AdditionalSplitData;
import work.zing.ergpower.pm5.event.AdditionalStatus1;
import work.zing.ergpower.pm5.event.AdditionalStatus2;
import work.zing.ergpower.pm5.event.AdditionalStrokeData;
import work.zing.ergpower.pm5.event.GeneralStatus;
import work.zing.ergpower.pm5.event.Pm5Event;
import work.zing.ergpower.pm5.event.RawFrame;
import work.zing.ergpower.pm5.event.SplitData;
import work.zing.ergpower.pm5.event.StrokeData;

/**
 * Encapsulates all firmware-specific PM5 wire interpretation: per-characteristic byte offsets,
 * lengths, present/absent fields, scaling, and the force-curve packet format (design decision D1/D2).
 *
 * <p>This base class implements every characteristic at the <b>rev-1.30 interface-definition</b>
 * layout — i.e. it <em>is</em> the reference profile ({@link ReferenceRev130}). A firmware whose wire
 * format has drifted extends this and <b>overrides only the characteristics that differ</b> (see
 * {@link CurrentPm5}). The decoder's cross-frame orchestration (force-curve reassembly, stroke
 * correlation) is firmware-independent and stays out of here.
 */
public abstract class FirmwareProfile {

    /** The PM5 reports force in pounds-force; ErgPower exposes SI Newtons. */
    protected static final double LBF_TO_NEWTON = 4.4482216152605;
    private static final int HEART_RATE_INVALID = 255;

    /** Stable identifier recorded into each session's manifest. */
    public abstract String id();

    /** Whether this profile applies to the given PM5 firmware revision string. */
    public boolean claims(String firmwareRevision) {
        return false;
    }

    /** Expected byte length for a characteristic (for the length fingerprint), or -1 if unknown. */
    public abstract int expectedLength(int characteristicId);

    // --- per-characteristic decoders (rev-1.30 defaults; override the drifted ones) ---

    /** {@code 0x0031} general status. */
    public Pm5Event generalStatus(byte[] b, Instant host) {
        if (b.length < 19) {
            return raw(0x31, b, host);
        }
        return new GeneralStatus(
                u24(b, 0) / 100.0, host,
                u24(b, 3) / 10.0,   // distance
                u8(b, 6), u8(b, 7), // workout type, interval type
                u8(b, 8), u8(b, 9), u8(b, 10), // workout/rowing/stroke state
                u24(b, 11),         // total work distance
                u24(b, 14),         // workout duration raw (0.01s if time, m if distance)
                u8(b, 17),          // workout duration type
                u8(b, 18));         // drag factor
    }

    /** {@code 0x0032} additional status 1. */
    public Pm5Event additionalStatus1(byte[] b, Instant host) {
        if (b.length < 17) {
            return raw(0x32, b, host);
        }
        int hr = u8(b, 6);
        return new AdditionalStatus1(
                u24(b, 0) / 100.0, host,
                u16(b, 3) / 1000.0, u8(b, 5),
                hr == HEART_RATE_INVALID ? null : hr,
                u16(b, 7) / 100.0, u16(b, 9) / 100.0,
                u16(b, 11), u24(b, 13) / 100.0, u8(b, 16));
    }

    /** {@code 0x0033} additional status 2 (rev-1.30: split fields at [6:8]/[8:10]). */
    public Pm5Event additionalStatus2(byte[] b, Instant host) {
        if (b.length < 10) {
            return raw(0x33, b, host);
        }
        return new AdditionalStatus2(
                u24(b, 0) / 100.0, host,
                u8(b, 3), u16(b, 4),
                u16(b, 6) / 100.0, u16(b, 8));
    }

    /** {@code 0x0035} stroke data (rev-1.30: stroke count at [16:18]). */
    public Pm5Event strokeData(byte[] b, Instant host) {
        if (b.length < 18) {
            return raw(0x35, b, host);
        }
        return new StrokeData(
                u24(b, 0) / 100.0, host,
                u24(b, 3) / 10.0,
                u8(b, 6) / 100.0, u8(b, 7) / 100.0,
                u16(b, 8) / 100.0, u16(b, 10) / 100.0,
                u16(b, 12) / 10.0 * LBF_TO_NEWTON,
                u16(b, 14) / 10.0 * LBF_TO_NEWTON,
                u16(b, 16));
    }

    /** {@code 0x0036} additional stroke data (offsets identical across the known firmwares). */
    public Pm5Event additionalStroke(byte[] b, Instant host) {
        if (b.length < 9) {
            return raw(0x36, b, host);
        }
        return new AdditionalStrokeData(
                u24(b, 0) / 100.0, host,
                u16(b, 3), u16(b, 7), u16(b, 5),
                b.length >= 12 ? u24(b, 9) : 0,
                b.length >= 15 ? u24(b, 12) : 0);
    }

    /** {@code 0x0037} split/interval data. */
    public Pm5Event splitData(byte[] b, Instant host) {
        if (b.length < 18) {
            return raw(0x37, b, host);
        }
        return new SplitData(
                u24(b, 0) / 100.0, host,
                u24(b, 3) / 10.0, u24(b, 6) / 10.0, u24(b, 9),
                u16(b, 12), u16(b, 14), u8(b, 16), u8(b, 17));
    }

    /** {@code 0x0038} additional split data (offsets identical across the known firmwares). */
    public Pm5Event additionalSplit(byte[] b, Instant host) {
        if (b.length < 16) {
            return raw(0x38, b, host);
        }
        int workHr = u8(b, 4);
        int restHr = u8(b, 5);
        return new AdditionalSplitData(
                u24(b, 0) / 100.0, host,
                u8(b, 3), workHr == 0 ? null : workHr, restHr == 0 ? null : restHr,
                u16(b, 6) / 10.0, u16(b, 8), u16(b, 10),
                u16(b, 12) / 1000.0, u16(b, 14));
    }

    /** {@code 0x003D} force-curve packet (rev-1.30 format). Returns {@code null} if too short. */
    public ForceCurvePacket forceCurvePacket(byte[] b) {
        if (b.length < 2) {
            return null;
        }
        int header = u8(b, 0);
        int words = header & 0x0F;
        double[] samples = new double[words];
        for (int i = 0; i < words; i++) {
            int off = 2 + 2 * i;
            if (off + 1 < b.length) {
                samples[i] = u16(b, off) * LBF_TO_NEWTON; // wire samples are pounds-force
            }
        }
        return new ForceCurvePacket(header >> 4, u8(b, 1), samples);
    }

    // --- shared helpers ---

    protected static RawFrame raw(int id, byte[] b, Instant host) {
        double pmTime = b.length >= 3 ? u24(b, 0) / 100.0 : Double.NaN;
        return new RawFrame(pmTime, host, id, b);
    }

    protected static int u8(byte[] b, int o) {
        return b[o] & 0xFF;
    }

    protected static int u16(byte[] b, int o) {
        return (b[o] & 0xFF) | ((b[o + 1] & 0xFF) << 8);
    }

    protected static int u24(byte[] b, int o) {
        return (b[o] & 0xFF) | ((b[o + 1] & 0xFF) << 8) | ((b[o + 2] & 0xFF) << 16);
    }
}
