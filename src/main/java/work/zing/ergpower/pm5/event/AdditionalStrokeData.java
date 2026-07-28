package work.zing.ergpower.pm5.event;

import java.time.Instant;

/**
 * Decoded {@code 0x0036} "additional stroke data" — fires exactly once per stroke, so its
 * {@link #strokeCount()} is the <b>canonical {@code strokeIndex}</b> for the whole pipeline
 * (and what the force curve is stamped with).
 *
 * <p><b>Firmware note:</b> 15 bytes on the captured hardware (rev&nbsp;1.30 says 17). Power [3:5] and
 * stroke count [7:9] are validated (power 0–206&nbsp;W, count 0…27). Stroke calories, and the projected
 * work time/distance fields, are decoded at spec offsets but not yet validated (the "Just Row" fixture
 * has no projection) — confirm with a fixed-piece live capture. Work-per-stroke does not fit this
 * firmware's 15-byte frame.
 *
 * @param pmTime                     PM5 elapsed time (s)
 * @param hostTime                   host receive time
 * @param strokePowerWatts           power for this stroke (W)
 * @param strokeCount                canonical monotonic stroke index
 * @param strokeCaloriesPerHour      stroke caloric burn rate (cal/hr)
 * @param projectedWorkTimeSeconds   projected total time (s; meaningful for fixed-distance pieces)
 * @param projectedWorkDistanceMeters projected total distance (m; meaningful for fixed-time pieces)
 */
public record AdditionalStrokeData(
        double pmTime,
        Instant hostTime,
        int strokePowerWatts,
        int strokeCount,
        int strokeCaloriesPerHour,
        double projectedWorkTimeSeconds,
        int projectedWorkDistanceMeters) implements Pm5Event {
}
