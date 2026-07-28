package work.zing.ergpower.pm5.event;

import java.time.Instant;

/**
 * Decoded {@code 0x0035} "stroke data" — per-stroke drive metrics.
 *
 * <p><b>Firmware note:</b> on the captured hardware this characteristic is <b>20 bytes</b>, not the
 * 18 of interface-definition rev&nbsp;1.30. An extra 2-byte field sits at offset [16:18] (identity
 * TBD, pending the current interface definition) and the <b>Stroke Count moved to [18:20]</b>. The
 * drive/force fields below are at their rev-1.30 offsets and were validated against the reassembled
 * force curve (peak drive force matches the curve peak). See {@code design.md}.
 *
 * @param pmTime               PM5 elapsed time (s)
 * @param hostTime             host receive time
 * @param distanceMeters       total distance (m)
 * @param driveLengthMeters    drive length (m)
 * @param driveTimeSeconds     drive time (s)
 * @param recoveryTimeSeconds  stroke recovery time (s)
 * @param strokeDistanceMeters distance for this stroke (m)
 * @param peakDriveForceNewtons peak drive force (N) — converted from the wire's 0.1-lb units
 * @param avgDriveForceNewtons  average drive force (N) — converted from the wire's 0.1-lb units
 * @param strokeCount          monotonic stroke index (from [18:20] on this firmware)
 */
public record StrokeData(
        double pmTime,
        Instant hostTime,
        double distanceMeters,
        double driveLengthMeters,
        double driveTimeSeconds,
        double recoveryTimeSeconds,
        double strokeDistanceMeters,
        double peakDriveForceNewtons,
        double avgDriveForceNewtons,
        int strokeCount) implements Pm5Event {
}
