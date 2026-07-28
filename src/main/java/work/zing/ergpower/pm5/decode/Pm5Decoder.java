package work.zing.ergpower.pm5.decode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import work.zing.ergpower.pm5.Pm5Frame;
import work.zing.ergpower.pm5.event.AdditionalStrokeData;
import work.zing.ergpower.pm5.event.ForceCurve;
import work.zing.ergpower.pm5.event.Pm5Event;
import work.zing.ergpower.pm5.event.RawFrame;
import work.zing.ergpower.pm5.event.StrokeData;
import work.zing.ergpower.pm5.firmware.CurrentPm5;
import work.zing.ergpower.pm5.firmware.FirmwareProfile;
import work.zing.ergpower.pm5.firmware.ForceCurvePacket;

/**
 * Turns raw {@link Pm5Frame}s into typed {@link Pm5Event}s.
 *
 * <p>Firmware-independent <b>orchestrator</b>: it owns only the cross-frame state that can't come
 * from a single frame — the multi-packet force-curve reassembly buffer and the last stroke identity
 * used to stamp the force curve — and delegates all byte interpretation to a swappable
 * {@link FirmwareProfile} (design decision D1). Stateful and <b>not thread-safe</b>: one per source.
 */
public final class Pm5Decoder {

    private static final int GENERAL_STATUS = 0x31;
    private static final int ADDITIONAL_STATUS_1 = 0x32;
    private static final int ADDITIONAL_STATUS_2 = 0x33;
    private static final int STROKE_DATA = 0x35;
    private static final int ADDITIONAL_STROKE = 0x36;
    private static final int SPLIT_DATA = 0x37;
    private static final int ADDITIONAL_SPLIT = 0x38;
    private static final int FORCE_CURVE = 0x3D;

    private final FirmwareProfile profile;

    // Correlation state: identity of the most recent stroke, stamped onto its force curve.
    private int lastStrokeCount = -1;
    private double lastStrokePmTime = Double.NaN;

    // Force-curve reassembly state.
    private int fcTotalPackets = 0;
    private int fcNextSeq = 0;
    private final List<Double> fcSamples = new ArrayList<>();

    /** Decode with the current-firmware profile (default). */
    public Pm5Decoder() {
        this(new CurrentPm5());
    }

    /** Decode with an explicit firmware profile. */
    public Pm5Decoder(FirmwareProfile profile) {
        this.profile = profile;
    }

    public FirmwareProfile profile() {
        return profile;
    }

    /** Decode one frame; usually one event, empty mid-curve, or one {@link ForceCurve} on completion. */
    public List<Pm5Event> decode(Pm5Frame frame) {
        byte[] b = frame.data();
        Instant host = frame.hostTime();
        return switch (frame.characteristicId()) {
            case GENERAL_STATUS -> List.of(profile.generalStatus(b, host));
            case ADDITIONAL_STATUS_1 -> List.of(profile.additionalStatus1(b, host));
            case ADDITIONAL_STATUS_2 -> List.of(profile.additionalStatus2(b, host));
            case STROKE_DATA -> remember(profile.strokeData(b, host));
            case ADDITIONAL_STROKE -> remember(profile.additionalStroke(b, host));
            case SPLIT_DATA -> List.of(profile.splitData(b, host));
            case ADDITIONAL_SPLIT -> List.of(profile.additionalSplit(b, host));
            case FORCE_CURVE -> decodeForceCurve(b, host);
            default -> List.of(rawFallback(frame.characteristicId(), b, host));
        };
    }

    /** Track the most recent stroke identity (from 0x35/0x36) for force-curve stamping. */
    private List<Pm5Event> remember(Pm5Event event) {
        if (event instanceof StrokeData s) {
            lastStrokeCount = s.strokeCount();
            lastStrokePmTime = s.pmTime();
        } else if (event instanceof AdditionalStrokeData a) {
            lastStrokeCount = a.strokeCount();
            lastStrokePmTime = a.pmTime();
        }
        return List.of(event);
    }

    /**
     * Reassemble a multi-packet force curve. Packet framing is parsed by the profile; the loop across
     * notifications and the stroke-key stamping are firmware-independent. Emits one {@link ForceCurve}
     * when the final packet arrives; discards a curve if a packet is missing or out of order.
     */
    private List<Pm5Event> decodeForceCurve(byte[] b, Instant host) {
        ForceCurvePacket packet = profile.forceCurvePacket(b);
        if (packet == null) {
            return List.of();
        }
        if (packet.sequence() == 0) {
            fcSamples.clear();
            fcTotalPackets = packet.totalPackets();
            append(packet);
            fcNextSeq = 1;
            return fcTotalPackets <= 1 ? emitCurve(host) : List.of();
        }
        if (fcNextSeq == 0 || packet.sequence() != fcNextSeq) {
            resetForceCurve();
            return List.of();
        }
        append(packet);
        fcNextSeq++;
        return fcNextSeq >= fcTotalPackets ? emitCurve(host) : List.of();
    }

    private void append(ForceCurvePacket packet) {
        for (double v : packet.forcesNewtons()) {
            fcSamples.add(v);
        }
    }

    private List<Pm5Event> emitCurve(Instant host) {
        double[] samples = new double[fcSamples.size()];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = fcSamples.get(i);
        }
        resetForceCurve();
        return List.of(new ForceCurve(lastStrokePmTime, host, lastStrokeCount, samples));
    }

    private void resetForceCurve() {
        fcSamples.clear();
        fcNextSeq = 0;
        fcTotalPackets = 0;
    }

    private static RawFrame rawFallback(int id, byte[] b, Instant host) {
        double pmTime = b.length >= 3
                ? ((b[0] & 0xFF) | ((b[1] & 0xFF) << 8) | ((b[2] & 0xFF) << 16)) / 100.0
                : Double.NaN;
        return new RawFrame(pmTime, host, id, b);
    }
}
