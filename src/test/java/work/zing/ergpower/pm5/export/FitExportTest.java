package work.zing.ergpower.pm5.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import work.zing.ergpower.pm5.source.ReplayPm5Source;
import work.zing.ergpower.pm5.storage.SessionMeta;
import work.zing.ergpower.pm5.storage.SessionStorage;

/**
 * Decodes the reference fixture into a real session, exports it as FIT, then decodes the FIT back and
 * asserts it's a valid rowing activity: 14-byte {@code .FIT} header, correct header + file CRC, and a
 * stream of records with file_id / session / activity messages and {@code sport = rowing}.
 */
class FitExportTest {

    private static final Path FIXTURE =
            Path.of("src/test/resources/captures/2026-07-28_rowerg_432234859_289m_28strokes.ndjson");

    @TempDir
    Path storage;

    @Test
    void exportsAValidRowingActivity() throws Exception {
        assumeTrue(Files.exists(FIXTURE), "fixture missing");
        SessionStorage.store(new ReplayPm5Source(FIXTURE), storage.resolve("s1"), SessionMeta.of("replay"));

        byte[] fit = new SessionFitExporter(storage).export("s1");
        assertValidRowingActivity(fit);
    }

    @Test
    void unknownSessionThrows() {
        assertThrows(NoSuchElementException.class, () -> new SessionFitExporter(storage).export("missing"));
    }

    private static void assertValidRowingActivity(byte[] fit) {
        assertTrue(fit.length > 16, "too short");
        assertEquals(14, fit[0] & 0xFF, "header size");
        assertEquals('.', fit[8]);
        assertEquals('F', fit[9]);
        assertEquals('I', fit[10]);
        assertEquals('T', fit[11]);
        assertEquals(Fit.crc16(fit, 12), u16(fit, 12), "header CRC");
        assertEquals(Fit.crc16(fit, fit.length - 2), u16(fit, fit.length - 2), "file CRC");
        assertEquals(fit.length - 16, u32(fit, 4), "data-size field"); // total - 14 header - 2 file CRC

        Map<Integer, int[]> localGlobalSize = new HashMap<>();     // local → {globalNum, msgSize}
        Map<Integer, List<int[]>> localFields = new HashMap<>();   // local → [{fieldNum, size}]
        Set<Integer> seen = new HashSet<>();
        int records = 0;
        Integer sport = null;

        int pos = 14;
        int end = fit.length - 2;
        while (pos < end) {
            int header = fit[pos++] & 0xFF;
            if ((header & 0x40) != 0) {
                int local = header & 0x0F;
                pos += 2; // reserved + architecture
                int global = u16(fit, pos);
                pos += 2;
                int nFields = fit[pos++] & 0xFF;
                List<int[]> fields = new ArrayList<>();
                int size = 0;
                for (int i = 0; i < nFields; i++) {
                    int fn = fit[pos] & 0xFF;
                    int fs = fit[pos + 1] & 0xFF;
                    pos += 3;
                    fields.add(new int[] {fn, fs});
                    size += fs;
                }
                if ((header & 0x20) != 0) { // developer fields section
                    int nDev = fit[pos++] & 0xFF;
                    for (int i = 0; i < nDev; i++) {
                        size += fit[pos + 1] & 0xFF;
                        pos += 3;
                    }
                }
                localGlobalSize.put(local, new int[] {global, size});
                localFields.put(local, fields);
            } else {
                int local = header & 0x0F;
                int[] gs = localGlobalSize.get(local);
                assertNotNull(gs, "data message before its definition");
                seen.add(gs[0]);
                if (gs[0] == 20) {
                    records++;
                }
                if (gs[0] == 18) { // session — read the sport enum (field 5)
                    int off = pos;
                    for (int[] f : localFields.get(local)) {
                        if (f[0] == 5) {
                            sport = fit[off] & 0xFF;
                        }
                        off += f[1];
                    }
                }
                pos += gs[1];
            }
        }

        assertTrue(seen.contains(0), "file_id present");
        assertTrue(seen.contains(20), "record present");
        assertTrue(seen.contains(19), "lap present");
        assertTrue(seen.contains(18), "session present");
        assertTrue(seen.contains(34), "activity present");
        assertTrue(seen.contains(23), "device_info present");
        assertTrue(seen.contains(206), "field_description present");
        assertTrue(seen.contains(207), "developer_data_id present");
        assertTrue(records > 0, "at least one record");
        assertEquals(15, sport, "sport = rowing");
    }

    private static int u16(byte[] b, int i) {
        return (b[i] & 0xFF) | ((b[i + 1] & 0xFF) << 8);
    }

    private static long u32(byte[] b, int i) {
        return (b[i] & 0xFFL) | ((b[i + 1] & 0xFFL) << 8) | ((b[i + 2] & 0xFFL) << 16) | ((b[i + 3] & 0xFFL) << 24);
    }
}
