package work.zing.ergpower.pm5.event;

import java.time.Instant;

/**
 * Decoded {@code 0x0031} "general status" — the timed heartbeat of a session (~1&nbsp;Hz by default).
 * 19 bytes, layout stable against interface-definition rev&nbsp;1.30 and confirmed on hardware.
 *
 * <p>Carries the workout <b>target</b> fields, so a viewer can show time-left / distance-left:
 * {@code workoutDurationSeconds - pmTime} for a fixed-time piece, {@code totalWorkDistanceMeters - distanceMeters}
 * for a fixed-distance piece (interpret by {@link #workoutDurationType()} / {@link #workoutType()}).
 *
 * <p>Target/duration units are per spec but not yet validated on a fixed workout (the fixture is a
 * "Just Row" where they are zero) — confirm with a fixed-piece live capture.
 *
 * @param pmTime                  PM5 elapsed time (s)
 * @param hostTime                host receive time
 * @param distanceMeters          total work distance so far (m)
 * @param workoutType             workout type enum (byte 6) — e.g. fixed distance vs fixed time
 * @param intervalType            interval type enum (byte 7)
 * @param workoutState            workout-state enum (byte 8) — drives auto start/stop
 * @param rowingState             rowing-state enum (byte 9)
 * @param strokeState             stroke-state enum (byte 10)
 * @param totalWorkDistanceMeters cumulative work distance across intervals (m; 0 for a single piece)
 * @param workoutDurationRaw      workout target — <b>units depend on {@link #workoutDurationType()}</b>:
 *                                0.01&nbsp;s when time-based (type 0), metres when distance-based (type 128).
 *                                Validated on hardware: 6000→60&nbsp;s time piece, 500→500&nbsp;m distance piece.
 * @param workoutDurationType     workout-duration type enum (byte 17): 0 = time, 128 = distance
 * @param dragFactor              drag factor (byte 18)
 */
public record GeneralStatus(
        double pmTime,
        Instant hostTime,
        double distanceMeters,
        int workoutType,
        int intervalType,
        int workoutState,
        int rowingState,
        int strokeState,
        int totalWorkDistanceMeters,
        int workoutDurationRaw,
        int workoutDurationType,
        int dragFactor) implements Pm5Event {

    /** Duration type indicating a fixed-time target (rev 1.30). */
    public static final int DURATION_TYPE_TIME = 0;
    /** Duration type indicating a fixed-distance target (rev 1.30). */
    public static final int DURATION_TYPE_DISTANCE = 128;

    /** Fixed-time target in seconds, or {@code null} if this piece is not time-based. */
    public Double targetTimeSeconds() {
        return workoutDurationType == DURATION_TYPE_TIME ? workoutDurationRaw / 100.0 : null;
    }

    /** Fixed-distance target in metres, or {@code null} if this piece is not distance-based. */
    public Integer targetDistanceMeters() {
        return workoutDurationType == DURATION_TYPE_DISTANCE ? workoutDurationRaw : null;
    }
}
