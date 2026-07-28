package work.zing.ergpower.pm5.firmware;

/**
 * The reference profile: the PM Bluetooth Smart Interface Definition rev 1.30 (2022) layout exactly
 * as {@link FirmwareProfile} implements it. Kept as the baseline other firmwares diverge from.
 */
public final class ReferenceRev130 extends FirmwareProfile {

    @Override
    public String id() {
        return "reference-rev1.30";
    }

    @Override
    public int expectedLength(int characteristicId) {
        return switch (characteristicId) {
            case 0x31 -> 19;
            case 0x32 -> 17;
            case 0x33 -> 18;
            case 0x35 -> 18;
            case 0x36 -> 17;
            case 0x37 -> 18;
            case 0x38 -> 18;
            default -> -1;
        };
    }
}
