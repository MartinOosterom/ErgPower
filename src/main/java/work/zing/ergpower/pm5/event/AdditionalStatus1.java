package work.zing.ergpower.pm5.event;

import java.time.Instant;

/**
 * Decoded {@code 0x0032} "additional status 1" — timed (~1&nbsp;Hz). 17 bytes, layout matches
 * interface-definition rev&nbsp;1.30 and validated on hardware (speed/stroke-rate/pace consistent
 * with the row; HR 255 = no belt).
 *
 * @param pmTime                 PM5 elapsed time (s)
 * @param hostTime               host receive time
 * @param speedMetersPerSecond   current speed (m/s)
 * @param strokeRate             stroke rate (strokes/min)
 * @param heartRateBpm           heart rate (bpm), or {@code null} when invalid/no belt (wire 255)
 * @param currentPaceSeconds     current pace (seconds per 500&nbsp;m)
 * @param avgPaceSeconds         average pace (seconds per 500&nbsp;m)
 * @param restDistanceMeters     rest distance (m; interval workouts)
 * @param restTimeSeconds        rest time (s; interval workouts)
 * @param ergMachineType         erg machine type enum (byte 16)
 */
public record AdditionalStatus1(
        double pmTime,
        Instant hostTime,
        double speedMetersPerSecond,
        int strokeRate,
        Integer heartRateBpm,
        double currentPaceSeconds,
        double avgPaceSeconds,
        int restDistanceMeters,
        double restTimeSeconds,
        int ergMachineType) implements Pm5Event {
}
