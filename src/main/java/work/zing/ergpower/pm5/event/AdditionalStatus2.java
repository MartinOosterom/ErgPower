package work.zing.ergpower.pm5.event;

import java.time.Instant;

/**
 * Decoded {@code 0x0033} "additional status 2" — timed (~1&nbsp;Hz).
 *
 * <p><b>Firmware note:</b> 20 bytes on the captured hardware (rev&nbsp;1.30 says 18); an extra 2-byte
 * field sits at [6:8] (identity TBD), shifting the split fields by +2. The offsets below are the
 * validated ones (split avg pace ≈ 135&nbsp;s and power ≈ 141&nbsp;W matched the row). The trailing
 * last-split fields decoded wrong on hardware, so they're dropped — authoritative split data comes
 * from {@link SplitData} / {@link AdditionalSplitData} ({@code 0x37}/{@code 0x38}).
 *
 * @param pmTime                  PM5 elapsed time (s)
 * @param hostTime                host receive time
 * @param intervalCount           workout interval count
 * @param totalCalories           total calories (cal)
 * @param splitAvgPaceSeconds     split/interval average pace (seconds per 500&nbsp;m)
 * @param splitAvgPowerWatts      split/interval average power (W)
 */
public record AdditionalStatus2(
        double pmTime,
        Instant hostTime,
        int intervalCount,
        int totalCalories,
        double splitAvgPaceSeconds,
        int splitAvgPowerWatts) implements Pm5Event {
}
