package work.zing.ergpower.pm5.export;

import java.io.ByteArrayOutputStream;

/**
 * Minimal, self-contained FIT (Flexible and Interoperable Data Transfer) encoder — enough to write a
 * rowing activity, with no external SDK (design D1). Little-endian throughout. Callers emit definition
 * messages then data messages against a local message type, writing each field's value in the exact
 * order the definition declared, then call {@link #toBytes()} for the finished file (14-byte header
 * with its CRC, the data records, and the trailing file CRC).
 *
 * <p>References: the FIT protocol is openly documented (base types, message headers, the 1989-12-31
 * epoch, and the two-nibble CRC-16 below).
 */
public final class Fit {

    // Base type bytes (high bit set for multi-byte types).
    public static final int ENUM = 0x00;
    public static final int UINT8 = 0x02;
    public static final int UINT16 = 0x84;
    public static final int UINT32 = 0x86;

    // "Invalid" sentinels used to omit a field that is present in the record layout.
    public static final int U8_INVALID = 0xFF;
    public static final int U16_INVALID = 0xFFFF;
    public static final long U32_INVALID = 0xFFFFFFFFL;

    /** FIT timestamps count seconds since 1989-12-31 00:00:00 UTC. */
    public static final long TS_EPOCH = 631065600L;

    private final ByteArrayOutputStream body = new ByteArrayOutputStream();

    /** Write a definition message. {@code fields} rows are {fieldDefNum, sizeBytes, baseType}. */
    public void definition(int local, int globalNum, int[][] fields) {
        body.write(0x40 | (local & 0x0F)); // definition message header
        body.write(0); // reserved
        body.write(0); // architecture: 0 = little-endian
        u16(body, globalNum);
        body.write(fields.length);
        for (int[] f : fields) {
            body.write(f[0]);
            body.write(f[1]);
            body.write(f[2]);
        }
    }

    /** Start a data message for a local type; follow with the field values in definition order. */
    public void data(int local) {
        body.write(local & 0x0F);
    }

    public void u8(int v) {
        body.write(v & 0xFF);
    }

    public void u16(int v) {
        u16(body, v);
    }

    public void u32(long v) {
        u32(body, v);
    }

    /** The finished FIT file: 14-byte header (+ header CRC), the data records, and the file CRC. */
    public byte[] toBytes() {
        byte[] data = body.toByteArray();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(14); // header size
        out.write(0x20); // protocol version 2.0
        u16(out, 2140); // profile version
        u32(out, data.length); // data records size
        out.write('.');
        out.write('F');
        out.write('I');
        out.write('T');
        u16(out, crc16(out.toByteArray(), 12)); // header CRC over the first 12 bytes
        out.writeBytes(data);
        u16(out, crc16(out.toByteArray(), out.size())); // file CRC over header + data
        return out.toByteArray();
    }

    private static void u16(ByteArrayOutputStream o, int v) {
        o.write(v & 0xFF);
        o.write((v >> 8) & 0xFF);
    }

    private static void u32(ByteArrayOutputStream o, long v) {
        o.write((int) (v & 0xFF));
        o.write((int) ((v >> 8) & 0xFF));
        o.write((int) ((v >> 16) & 0xFF));
        o.write((int) ((v >> 24) & 0xFF));
    }

    /** The FIT CRC-16 (nibble-table algorithm from the FIT spec). */
    static int crc16(byte[] data, int len) {
        int[] table = {0x0000, 0xCC01, 0xD801, 0x1400, 0xF001, 0x3C00, 0x2800, 0xE401,
                0xA001, 0x6C00, 0x7800, 0xB401, 0x5000, 0x9C01, 0x8801, 0x4400};
        int crc = 0;
        for (int i = 0; i < len; i++) {
            int b = data[i] & 0xFF;
            int tmp = table[crc & 0xF];
            crc = (crc >> 4) & 0x0FFF;
            crc = crc ^ tmp ^ table[b & 0xF];
            tmp = table[crc & 0xF];
            crc = (crc >> 4) & 0x0FFF;
            crc = crc ^ tmp ^ table[(b >> 4) & 0xF];
        }
        return crc & 0xFFFF;
    }
}
