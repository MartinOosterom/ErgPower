package work.zing.ergpower.pm5.firmware;

/**
 * One parsed force-curve notification packet ({@code 0x003D}). The reassembly loop across packets
 * lives in {@code Pm5Decoder}; the packet framing (nibble meaning, sample encoding) is firmware
 * specific and lives in the {@link FirmwareProfile}.
 *
 * @param totalPackets number of packets that make up this stroke's curve (from the header high nibble)
 * @param sequence     this packet's sequence number
 * @param forcesNewtons the samples in this packet, already converted to Newtons
 */
public record ForceCurvePacket(int totalPackets, int sequence, double[] forcesNewtons) {

    public ForceCurvePacket {
        forcesNewtons = forcesNewtons.clone();
    }

    @Override
    public double[] forcesNewtons() {
        return forcesNewtons.clone();
    }
}
