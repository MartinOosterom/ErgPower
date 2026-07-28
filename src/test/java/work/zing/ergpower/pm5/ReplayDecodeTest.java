package work.zing.ergpower.pm5;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import work.zing.ergpower.pm5.event.AdditionalStatus1;
import work.zing.ergpower.pm5.event.AdditionalStatus2;
import work.zing.ergpower.pm5.event.AdditionalStrokeData;
import work.zing.ergpower.pm5.event.ForceCurve;
import work.zing.ergpower.pm5.event.GeneralStatus;
import work.zing.ergpower.pm5.event.Pm5Event;
import work.zing.ergpower.pm5.event.StrokeData;
import work.zing.ergpower.pm5.source.ReplayPm5Source;

/**
 * Decodes the real captured row (289.7&nbsp;m / 28 strokes from PM5 432234859) end-to-end through
 * {@link ReplayPm5Source} and asserts the values we observed live. This is the pipeline's regression
 * anchor: the decoder, the force-curve reassembly, and the source seam all validated against real
 * bytes — no PM5 required.
 */
class ReplayDecodeTest {

    private static final Path FIXTURE =
            Path.of("src/test/resources/captures/2026-07-28_rowerg_432234859_289m_28strokes.ndjson");

    @Test
    void decodesRealCaptureToExpectedValues() {
        assumeTrue(Files.exists(FIXTURE), "capture fixture not present: " + FIXTURE.toAbsolutePath());

        List<Pm5Event> events = new ReplayPm5Source(FIXTURE).events().collectList().block();
        assertNotNull(events);

        long generalStatus = events.stream().filter(GeneralStatus.class::isInstance).count();
        long strokeData = events.stream().filter(StrokeData.class::isInstance).count();
        long additionalStroke = events.stream().filter(AdditionalStrokeData.class::isInstance).count();
        long forceCurves = events.stream().filter(ForceCurve.class::isInstance).count();

        int maxPower = events.stream().filter(AdditionalStrokeData.class::isInstance)
                .mapToInt(e -> ((AdditionalStrokeData) e).strokePowerWatts()).max().orElse(-1);
        int maxStrokeCount = events.stream().filter(AdditionalStrokeData.class::isInstance)
                .mapToInt(e -> ((AdditionalStrokeData) e).strokeCount()).max().orElse(-1);
        double maxDistance = events.stream().filter(GeneralStatus.class::isInstance)
                .mapToDouble(e -> ((GeneralStatus) e).distanceMeters()).max().orElse(-1);

        System.out.printf(
                "decoded: generalStatus=%d strokeData=%d additionalStroke=%d forceCurves=%d "
                        + "maxPower=%dW maxStrokeCount=%d maxDistance=%.1fm%n",
                generalStatus, strokeData, additionalStroke, forceCurves,
                maxPower, maxStrokeCount, maxDistance);

        // Frame counts from the capture.
        assertEquals(295, generalStatus, "0x31 general-status frames");
        assertEquals(53, strokeData, "0x35 stroke-data frames");
        assertEquals(28, additionalStroke, "0x36 additional-stroke frames (once per stroke)");

        // Values observed live.
        assertEquals(206, maxPower, "peak stroke power (W)");
        assertEquals(27, maxStrokeCount, "0..27 => 28 strokes");
        assertEquals(289.7, maxDistance, 0.2, "final distance (m)");

        // Force curves reassembled (~26 strokes with a curve).
        assertTrue(forceCurves >= 25 && forceCurves <= 28, "force curves ~26, got " + forceCurves);

        // A curve must have real content, and its peak amplitude tracks drive force (Newtons).
        ForceCurve curve = events.stream().filter(ForceCurve.class::isInstance)
                .map(ForceCurve.class::cast).findFirst().orElseThrow();
        assertTrue(curve.length() > 20, "curve should have many points, had " + curve.length());
        assertTrue(curve.peak() > 200, "curve peak (N) should be substantial, was " + curve.peak());
        assertTrue(curve.strokeCount() >= 0, "force curve should be stamped with a stroke index");

        // Additional status 1 (0x32): plausible stroke rate + speed, HR absent (no belt = null).
        int maxStrokeRate = events.stream().filter(AdditionalStatus1.class::isInstance)
                .mapToInt(e -> ((AdditionalStatus1) e).strokeRate()).max().orElse(-1);
        double maxSpeed = events.stream().filter(AdditionalStatus1.class::isInstance)
                .mapToDouble(e -> ((AdditionalStatus1) e).speedMetersPerSecond()).max().orElse(-1);
        boolean anyHeartRate = events.stream().filter(AdditionalStatus1.class::isInstance)
                .anyMatch(e -> ((AdditionalStatus1) e).heartRateBpm() != null);
        assertTrue(maxStrokeRate >= 15 && maxStrokeRate <= 60, "stroke rate spm, got " + maxStrokeRate);
        assertTrue(maxSpeed > 2.0 && maxSpeed < 8.0, "speed m/s, got " + maxSpeed);
        assertFalse(anyHeartRate, "no HR belt was worn, so heart rate should be null (wire 255)");

        // Additional status 2 (0x33): split avg power in a sane range (validated +2 offset shift).
        int maxSplitPower = events.stream().filter(AdditionalStatus2.class::isInstance)
                .mapToInt(e -> ((AdditionalStatus2) e).splitAvgPowerWatts()).max().orElse(-1);
        assertTrue(maxSplitPower > 50 && maxSplitPower < 600, "split avg power W, got " + maxSplitPower);
    }
}
