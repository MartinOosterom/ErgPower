package work.zing.ergpower.pm5.event;

import java.time.Instant;

/**
 * Decoded {@code 0x0037} "split/interval data" — fires at each split/interval boundary. 18 bytes,
 * layout matches rev&nbsp;1.30 and <b>validated on hardware</b> (100&nbsp;m auto-splits of a 500&nbsp;m
 * piece: distances 100/200/300/400&nbsp;m, ~23.6&nbsp;s each).
 *
 * @param pmTime               PM5 elapsed time (s)
 * @param hostTime             host receive time
 * @param distanceMeters       total distance at the split (m)
 * @param splitTimeSeconds     time for this split (s)
 * @param splitDistanceMeters  distance of this split (m)
 * @param restTimeSeconds      interval rest time (s)
 * @param restDistanceMeters   interval rest distance (m)
 * @param splitType            split/interval type enum
 * @param splitNumber          split/interval number (1-based)
 */
public record SplitData(
        double pmTime,
        Instant hostTime,
        double distanceMeters,
        double splitTimeSeconds,
        int splitDistanceMeters,
        int restTimeSeconds,
        int restDistanceMeters,
        int splitType,
        int splitNumber) implements Pm5Event {
}
