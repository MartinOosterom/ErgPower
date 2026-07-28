package work.zing.ergpower.pm5.event;

import java.time.Instant;
import java.util.Arrays;

/**
 * A reassembled per-stroke force/power curve from characteristic {@code 0x003D}, in SI units.
 *
 * <p>The PM5 delivers a curve across several successive notifications; the decoder reassembles them
 * (rev-1.30 format: header nibbles = total-packets / points-per-packet, then a sequence number, then
 * little-endian 16-bit samples in pounds-force — confirmed on hardware) and converts each sample to
 * <b>Newtons</b>. Because {@code 0x003D} carries no stroke identity, the {@link #strokeCount()} and
 * {@link #pmTime()} here are <b>resolved at decode time</b> from the concurrent stroke event, so
 * downstream matchability never depends on the raw payload.
 *
 * @param pmTime         PM5 elapsed time of the owning stroke (s)
 * @param hostTime       host receive time of the completing packet
 * @param strokeCount    owning stroke index (resolved from {@code 0x0036})
 * @param forcesNewtons  ordered drive-force samples (N); peak matches the stroke's Peak Drive Force
 */
public record ForceCurve(
        double pmTime,
        Instant hostTime,
        int strokeCount,
        double[] forcesNewtons) implements Pm5Event {

    /** Defensive copy — arrays are mutable. */
    public ForceCurve {
        forcesNewtons = forcesNewtons.clone();
    }

    @Override
    public double[] forcesNewtons() {
        return forcesNewtons.clone();
    }

    /** Peak force in Newtons (0 for an empty curve). */
    public double peak() {
        return Arrays.stream(forcesNewtons).max().orElse(0.0);
    }

    public int length() {
        return forcesNewtons.length;
    }
}
