package work.zing.ergpower.pm5.event;

import java.time.Instant;

/**
 * Decoded {@code 0x0038} "additional split/interval data" — per-split aggregates. 19 bytes on the
 * captured firmware; the leading fields are <b>validated on hardware</b> (pace/power/spm/speed all
 * consistent with the 500&nbsp;m piece's splits).
 *
 * @param pmTime               PM5 elapsed time (s)
 * @param hostTime             host receive time
 * @param avgStrokeRate        split average stroke rate (strokes/min)
 * @param workHeartRateBpm     split work heart rate (bpm), or {@code null} if none (wire 0)
 * @param restHeartRateBpm     split rest heart rate (bpm), or {@code null} if none (wire 0)
 * @param avgPaceSeconds       split average pace (seconds per 500&nbsp;m)
 * @param totalCalories        split total calories (cal)
 * @param avgCaloriesPerHour   split average calories (cal/hr)
 * @param speedMetersPerSecond split average speed (m/s)
 * @param powerWatts           split average power (W)
 */
public record AdditionalSplitData(
        double pmTime,
        Instant hostTime,
        int avgStrokeRate,
        Integer workHeartRateBpm,
        Integer restHeartRateBpm,
        double avgPaceSeconds,
        int totalCalories,
        int avgCaloriesPerHour,
        double speedMetersPerSecond,
        int powerWatts) implements Pm5Event {
}
