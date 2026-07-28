package work.zing.ergpower.pm5;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import work.zing.ergpower.pm5.event.GeneralStatus;
import work.zing.ergpower.pm5.event.StrokeData;
import work.zing.ergpower.pm5.firmware.CurrentPm5;
import work.zing.ergpower.pm5.firmware.FirmwareProfile;
import work.zing.ergpower.pm5.firmware.ReferenceRev130;

/**
 * Verifies the firmware profiles diverge <b>exactly</b> where the wire drifted (0x33/0x35 offsets)
 * and agree everywhere else — the guarantee that makes profiles pluggable.
 */
class FirmwareProfileTest {

    private static final FirmwareProfile REF = new ReferenceRev130();
    private static final FirmwareProfile CUR = new CurrentPm5();

    @Test
    void strokeCountOffsetDiffersBetweenProfiles() {
        // 20-byte 0x35 frame: [16:18]=99 (rev-1.30 position), [18:20]=7 (current position)
        byte[] b = new byte[20];
        b[16] = 99;
        b[18] = 7;

        StrokeData current = (StrokeData) CUR.strokeData(b, Instant.EPOCH);
        StrokeData reference = (StrokeData) REF.strokeData(b, Instant.EPOCH);

        assertEquals(7, current.strokeCount(), "current firmware reads stroke count at [18:20]");
        assertEquals(99, reference.strokeCount(), "rev-1.30 reads stroke count at [16:18]");
        assertNotEquals(current.strokeCount(), reference.strokeCount(),
                "0x35 stroke-count offset is where the firmwares diverge");
    }

    @Test
    void status2SplitFieldsShiftBetweenProfiles() {
        // 20-byte 0x33 frame: put avg power 200 at current [10:12], and 55 at rev-1.30 [8:10]
        byte[] b = new byte[20];
        b[8] = 55;    // rev-1.30 avg power
        b[10] = (byte) 200; // current avg power
        var current = (work.zing.ergpower.pm5.event.AdditionalStatus2) CUR.additionalStatus2(b, Instant.EPOCH);
        var reference = (work.zing.ergpower.pm5.event.AdditionalStatus2) REF.additionalStatus2(b, Instant.EPOCH);
        assertEquals(200, current.splitAvgPowerWatts());
        assertEquals(55, reference.splitAvgPowerWatts());
    }

    @Test
    void generalStatusAgreesAcrossProfiles() {
        // 0x31 did not drift — both profiles must decode it identically
        byte[] b = new byte[19];
        b[3] = (byte) 0xE8;
        b[4] = 0x03;       // distance 1000 * 0.1 = 100.0 m
        b[18] = 120;        // drag factor
        GeneralStatus current = (GeneralStatus) CUR.generalStatus(b, Instant.EPOCH);
        GeneralStatus reference = (GeneralStatus) REF.generalStatus(b, Instant.EPOCH);
        assertEquals(reference.distanceMeters(), current.distanceMeters());
        assertEquals(reference.dragFactor(), current.dragFactor());
        assertEquals(100.0, current.distanceMeters(), 0.01);
    }

    @Test
    void profilesDeclareTheirFingerprintLengths() {
        assertEquals(18, REF.expectedLength(0x35));
        assertEquals(20, CUR.expectedLength(0x35));
        assertEquals(18, REF.expectedLength(0x33));
        assertEquals(20, CUR.expectedLength(0x33));
    }
}
