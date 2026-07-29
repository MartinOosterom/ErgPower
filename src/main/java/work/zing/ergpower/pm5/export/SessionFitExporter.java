package work.zing.ergpower.pm5.export;

import static work.zing.ergpower.pm5.export.Fit.BYTE;
import static work.zing.ergpower.pm5.export.Fit.ENUM;
import static work.zing.ergpower.pm5.export.Fit.STRING;
import static work.zing.ergpower.pm5.export.Fit.TS_EPOCH;
import static work.zing.ergpower.pm5.export.Fit.U16_INVALID;
import static work.zing.ergpower.pm5.export.Fit.U32_INVALID;
import static work.zing.ergpower.pm5.export.Fit.U8_INVALID;
import static work.zing.ergpower.pm5.export.Fit.UINT16;
import static work.zing.ergpower.pm5.export.Fit.UINT32;
import static work.zing.ergpower.pm5.export.Fit.UINT8;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import work.zing.ergpower.pm5.config.ErgPowerBleProperties;

/**
 * Builds a data-rich Garmin .FIT rowing activity from a stored session (design of change
 * {@code enrich-fit-export}). Recombines the per-characteristic NDJSON by {@code pmTime} into:
 * per-status records (distance, speed, power, heart rate, stroke cadence, stroke distance) with
 * <b>developer fields</b> for drag and per-stroke drive/force; one <b>rich lap</b> per split (avg/max
 * power, HR, cadence, speed + calories + strokes); a session summary; and {@code device_info}. Force
 * curves are intentionally excluded (they don't map to FIT).
 */
@Component
public class SessionFitExporter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Global message numbers.
    private static final int MSG_FILE_ID = 0, MSG_SESSION = 18, MSG_LAP = 19, MSG_RECORD = 20,
            MSG_EVENT = 21, MSG_DEVICE_INFO = 23, MSG_ACTIVITY = 34, MSG_FIELD_DESC = 206, MSG_DEV_ID = 207;
    // Local message types (definition slots).
    private static final int L_RECORD = 0, L_FILE_ID = 1, L_EVENT = 2, L_LAP = 3, L_SESSION = 4,
            L_ACTIVITY = 5, L_DEV_ID = 6, L_FIELD_DESC = 7, L_DEVICE = 8;
    private static final int SPORT_ROWING = 15;

    // Developer field numbers (developer_data_index 0).
    private static final int DF_DRAG = 0, DF_STROKES = 1, DF_DRIVE_TIME = 2, DF_RECOVERY = 3,
            DF_DRIVE_LEN = 4, DF_PEAK_FORCE = 5, DF_AVG_FORCE = 6, DF_FIRMWARE = 7;
    private static final int NAME_SIZE = 20, TEXT_SIZE = 24;
    private static final byte[] APP_ID = {
            (byte) 0xE7, 0x60, 0x77, 0x00, 0x00, 0x00, 0x40, 0x00,
            (byte) 0x80, 0x00, 0x00, 0x08, 0x00, 0x2C, (byte) 0x9A, 0x66}; // arbitrary, stable app id

    private final Path storageDir;

    @Autowired
    public SessionFitExporter(ErgPowerBleProperties props) {
        this(Path.of(props.storage().dir()));
    }

    SessionFitExporter(Path storageDir) {
        this.storageDir = storageDir;
    }

    /** Encode the stored session as FIT bytes. Throws {@link NoSuchElementException} if it doesn't exist. */
    public byte[] export(String id) throws IOException {
        Path dir = storageDir.resolve(id);
        if (!Files.isDirectory(dir)) {
            throw new NoSuchElementException(id);
        }

        JsonNode session = readJson(dir.resolve("session.json"));
        JsonNode summary = readJson(dir.resolve("summary.json"));
        List<JsonNode> general = readLines(dir.resolve("status-general.ndjson"));
        long startUnix = resolveStart(session, general);
        String device = text(session, "device", "Concept2 PM5");
        String firmware = text(session, "firmware", "");

        Map<Long, JsonNode> add1 = new HashMap<>();
        for (JsonNode n : readLines(dir.resolve("status-additional1.ndjson"))) {
            if (n.hasNonNull("pmTime")) {
                add1.put(pmKey(n.get("pmTime").asDouble()), n);
            }
        }
        List<double[]> powers = sortedPmValues(dir.resolve("stroke-additional.ndjson"), "strokePowerW");
        List<JsonNode> strokes = readLines(dir.resolve("stroke.ndjson"));
        strokes.sort(Comparator.comparingDouble(n -> n.path("pmTime").asDouble()));

        Fit fit = new Fit();

        // --- file_id ---
        fit.definition(L_FILE_ID, MSG_FILE_ID, new int[][] {{0, 1, ENUM}, {1, 2, UINT16}, {2, 2, UINT16}, {4, 4, UINT32}});
        fit.data(L_FILE_ID);
        fit.u8(4);
        fit.u16(255);
        fit.u16(0);
        fit.u32(fit(startUnix, 0));

        // --- developer_data_id + field descriptions ---
        fit.definition(L_DEV_ID, MSG_DEV_ID, new int[][] {{1, 16, BYTE}, {3, 1, UINT8}});
        fit.data(L_DEV_ID);
        fit.bytes(APP_ID);
        fit.u8(0);
        fit.definition(L_FIELD_DESC, MSG_FIELD_DESC, new int[][] {{0, 1, UINT8}, {1, 1, UINT8}, {2, 1, UINT8}, {3, NAME_SIZE, STRING}});
        fieldDesc(fit, DF_DRAG, UINT8, "drag_factor");
        fieldDesc(fit, DF_STROKES, UINT16, "stroke_count");
        fieldDesc(fit, DF_DRIVE_TIME, UINT16, "drive_time_ms");
        fieldDesc(fit, DF_RECOVERY, UINT16, "recovery_time_ms");
        fieldDesc(fit, DF_DRIVE_LEN, UINT16, "drive_length_cm");
        fieldDesc(fit, DF_PEAK_FORCE, UINT16, "peak_drive_force_n");
        fieldDesc(fit, DF_AVG_FORCE, UINT16, "avg_drive_force_n");
        fieldDesc(fit, DF_FIRMWARE, STRING, "firmware");

        // --- device_info ---
        fit.definition(L_DEVICE, MSG_DEVICE_INFO, new int[][] {{253, 4, UINT32}, {2, 2, UINT16}, {4, 2, UINT16}, {27, TEXT_SIZE, STRING}});
        fit.data(L_DEVICE);
        fit.u32(fit(startUnix, 0));
        fit.u16(255);
        fit.u16(0);
        fit.str(device, TEXT_SIZE);

        // --- event: timer start ---
        fit.definition(L_EVENT, MSG_EVENT, new int[][] {{253, 4, UINT32}, {0, 1, ENUM}, {1, 1, ENUM}});
        fit.data(L_EVENT);
        fit.u32(fit(startUnix, 0));
        fit.u8(0);
        fit.u8(0);

        // --- records (standard fields + developer fields) ---
        fit.definition(L_RECORD, MSG_RECORD,
                new int[][] {{253, 4, UINT32}, {5, 4, UINT32}, {6, 2, UINT16}, {7, 2, UINT16}, {3, 1, UINT8}, {4, 1, UINT8}, {12, 2, UINT16}},
                new int[][] {{DF_DRAG, 1, 0}, {DF_STROKES, 2, 0}, {DF_DRIVE_TIME, 2, 0}, {DF_RECOVERY, 2, 0},
                        {DF_DRIVE_LEN, 2, 0}, {DF_PEAK_FORCE, 2, 0}, {DF_AVG_FORCE, 2, 0}});

        double speedSum = 0, speedMax = 0, lastPm = 0, lastDistance = 0;
        int speedCount = 0, hrSum = 0, hrMax = 0, hrCount = 0, cadSum = 0, cadMax = 0, cadCount = 0;
        int pi = 0, si = 0, curPower = -1;
        JsonNode stroke = null;
        List<double[]> samples = new ArrayList<>(); // {pm, hr, power, mps, cad} (-1 = missing)

        for (JsonNode g : general) {
            if (!g.hasNonNull("pmTime")) {
                continue;
            }
            double pm = g.get("pmTime").asDouble();
            lastPm = pm;
            while (pi < powers.size() && powers.get(pi)[0] <= pm + 1e-6) {
                curPower = (int) powers.get(pi++)[1];
            }
            while (si < strokes.size() && strokes.get(si).path("pmTime").asDouble() <= pm + 1e-6) {
                stroke = strokes.get(si++);
            }
            JsonNode a1 = add1.get(pmKey(pm));
            double distanceM = g.path("distanceM").asDouble(-1);
            if (distanceM >= 0) {
                lastDistance = distanceM;
            }
            Integer hr = a1 != null && a1.hasNonNull("heartRateBpm") ? a1.get("heartRateBpm").asInt() : null;
            Integer spm = a1 != null && a1.hasNonNull("strokeRate") ? a1.get("strokeRate").asInt() : null;
            double mps = a1 != null && a1.hasNonNull("speedMps") ? a1.get("speedMps").asDouble() : -1;
            int drag = g.path("dragFactor").asInt(-1);
            double strokeDist = stroke != null ? stroke.path("strokeDistanceM").asDouble(-1) : -1;

            fit.data(L_RECORD);
            fit.u32(fit(startUnix, pm));
            fit.u32(distanceM >= 0 ? Math.round(distanceM * 100) : U32_INVALID);
            fit.u16(mps >= 0 ? (int) Math.round(mps * 1000) : U16_INVALID);
            fit.u16(curPower >= 0 ? curPower : U16_INVALID);
            fit.u8(hr != null ? hr : U8_INVALID);
            fit.u8(spm != null ? spm : U8_INVALID);
            fit.u16(strokeDist >= 0 ? (int) Math.round(strokeDist * 100) : U16_INVALID);
            // developer fields
            fit.u8(drag >= 0 ? drag : U8_INVALID);
            fit.u16(strokeU16(stroke, "strokeCount", 1));
            fit.u16(strokeMillis(stroke, "driveTimeS"));
            fit.u16(strokeMillis(stroke, "recoveryTimeS"));
            fit.u16(strokeCenti(stroke, "driveLengthM"));
            fit.u16(strokeU16(stroke, "peakDriveForceN", 1));
            fit.u16(strokeU16(stroke, "avgDriveForceN", 1));

            if (mps >= 0) { speedSum += mps; speedMax = Math.max(speedMax, mps); speedCount++; }
            if (hr != null) { hrSum += hr; hrMax = Math.max(hrMax, hr); hrCount++; }
            if (spm != null) { cadSum += spm; cadMax = Math.max(cadMax, spm); cadCount++; }
            samples.add(new double[] {pm, hr != null ? hr : -1, curPower, mps, spm != null ? spm : -1});
        }

        double durationS = num(summary, "durationS", lastPm);
        double totalDistanceM = num(summary, "distanceM", lastDistance);
        long endFit = fit(startUnix, durationS);

        // --- event: timer stop ---
        fit.data(L_EVENT);
        fit.u32(endFit);
        fit.u8(0);
        fit.u8(4);

        // --- rich laps ---
        fit.definition(L_LAP, MSG_LAP, new int[][] {
                {253, 4, UINT32}, {254, 2, UINT16}, {2, 4, UINT32}, {7, 4, UINT32}, {8, 4, UINT32}, {9, 4, UINT32},
                {10, 4, UINT32}, {11, 2, UINT16}, {13, 2, UINT16}, {14, 2, UINT16}, {15, 1, UINT8}, {16, 1, UINT8},
                {17, 1, UINT8}, {18, 1, UINT8}, {19, 2, UINT16}, {20, 2, UINT16}});
        List<JsonNode> splits = readLines(dir.resolve("split.ndjson"));
        List<JsonNode> splitAdd = readLines(dir.resolve("split-additional.ndjson"));
        int numLaps;
        if (splits.isEmpty()) {
            writeLap(fit, 0, fit(startUnix, 0), endFit, durationS, totalDistanceM, strokes.size(),
                    (int) num(summary, "avgPowerW", -1), (int) num(summary, "peakPowerW", -1),
                    avg(speedSum, speedCount), speedMax, hrCount > 0 ? hrSum / hrCount : -1, hrMax,
                    cadCount > 0 ? cadSum / cadCount : -1, cadMax, U16_INVALID);
            numLaps = 1;
        } else {
            for (int i = 0; i < splits.size(); i++) {
                JsonNode sp = splits.get(i);
                JsonNode sa = i < splitAdd.size() ? splitAdd.get(i) : null;
                double pm = sp.hasNonNull("pmTime") ? sp.get("pmTime").asDouble() : durationS;
                double splitTime = sp.path("splitTimeS").asDouble(0);
                double splitDist = sp.path("splitDistanceM").asDouble(0);
                double from = pm - splitTime;
                int[] mx = maxInWindow(samples, from, pm);      // {maxHr, maxPower, maxSpeedMmps, maxCad}
                int strokesInLap = countInWindow(strokes, from, pm);
                writeLap(fit, i, fit(startUnix, from), fit(startUnix, pm), splitTime, splitDist, strokesInLap,
                        num(sa, "powerW", -1) >= 0 ? (int) num(sa, "powerW", -1) : -1, mx[1],
                        num(sa, "speedMps", -1), mx[3] / 1000.0,
                        num(sa, "workHeartRateBpm", -1) >= 0 ? (int) num(sa, "workHeartRateBpm", -1) : -1, mx[0],
                        num(sa, "avgStrokeRate", -1) >= 0 ? (int) num(sa, "avgStrokeRate", -1) : -1, mx[2],
                        num(sa, "totalCalories", -1) >= 0 ? (int) num(sa, "totalCalories", -1) : U16_INVALID);
            }
            numLaps = splits.size();
        }

        // --- session (with total calories, total strokes, and firmware developer field) ---
        int totalCalories = lastInt(dir.resolve("status-additional2.ndjson"), "totalCalories");
        int totalStrokes = (int) num(summary, "strokes", 0);
        fit.definition(L_SESSION, MSG_SESSION, new int[][] {
                {253, 4, UINT32}, {254, 2, UINT16}, {2, 4, UINT32}, {7, 4, UINT32}, {8, 4, UINT32}, {9, 4, UINT32},
                {10, 4, UINT32}, {11, 2, UINT16}, {5, 1, ENUM}, {6, 1, ENUM}, {14, 2, UINT16}, {15, 2, UINT16},
                {20, 2, UINT16}, {21, 2, UINT16}, {16, 1, UINT8}, {17, 1, UINT8}, {18, 1, UINT8}, {19, 1, UINT8}, {26, 2, UINT16}},
                new int[][] {{DF_FIRMWARE, TEXT_SIZE, 0}});
        fit.data(L_SESSION);
        fit.u32(endFit);
        fit.u16(0);
        fit.u32(fit(startUnix, 0));
        fit.u32(Math.round(durationS * 1000));
        fit.u32(Math.round(durationS * 1000));
        fit.u32(Math.round(totalDistanceM * 100));
        fit.u32(totalStrokes);
        fit.u16(totalCalories);
        fit.u8(SPORT_ROWING);
        fit.u8(0);
        fit.u16(speedCount > 0 ? (int) Math.round(avg(speedSum, speedCount) * 1000) : U16_INVALID);
        fit.u16(speedCount > 0 ? (int) Math.round(speedMax * 1000) : U16_INVALID);
        fit.u16((int) num(summary, "avgPowerW", -1) >= 0 ? (int) num(summary, "avgPowerW", -1) : U16_INVALID);
        fit.u16((int) num(summary, "peakPowerW", -1) >= 0 ? (int) num(summary, "peakPowerW", -1) : U16_INVALID);
        fit.u8(hrCount > 0 ? hrSum / hrCount : U8_INVALID);
        fit.u8(hrCount > 0 ? hrMax : U8_INVALID);
        fit.u8(cadCount > 0 ? cadSum / cadCount : U8_INVALID);
        fit.u8(cadCount > 0 ? cadMax : U8_INVALID);
        fit.u16(numLaps);
        fit.str(firmware, TEXT_SIZE);

        // --- activity ---
        fit.definition(L_ACTIVITY, MSG_ACTIVITY, new int[][] {
                {253, 4, UINT32}, {0, 4, UINT32}, {1, 2, UINT16}, {2, 1, ENUM}, {3, 1, ENUM}, {4, 1, ENUM}});
        fit.data(L_ACTIVITY);
        fit.u32(endFit);
        fit.u32(Math.round(durationS * 1000));
        fit.u16(1);
        fit.u8(0);
        fit.u8(26);
        fit.u8(1);

        return fit.toBytes();
    }

    private static void fieldDesc(Fit fit, int num, int baseType, String name) {
        fit.data(L_FIELD_DESC);
        fit.u8(0);
        fit.u8(num);
        fit.u8(baseType);
        fit.str(name, NAME_SIZE);
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private static void writeLap(Fit fit, int index, long startFit, long endFit, double elapsedS,
            double distanceM, int strokes, int avgPower, int maxPower, double avgMps, double maxMps,
            int avgHr, int maxHr, int avgCad, int maxCad, int calories) {
        fit.data(L_LAP);
        fit.u32(endFit);
        fit.u16(index);
        fit.u32(startFit);
        fit.u32(Math.round(elapsedS * 1000));
        fit.u32(Math.round(elapsedS * 1000));
        fit.u32(Math.round(distanceM * 100));
        fit.u32(strokes >= 0 ? strokes : (int) U32_INVALID);
        fit.u16(calories);
        fit.u16(avgMps >= 0 ? (int) Math.round(avgMps * 1000) : U16_INVALID);
        fit.u16(maxMps >= 0 ? (int) Math.round(maxMps * 1000) : U16_INVALID);
        fit.u8(avgHr >= 0 ? avgHr : U8_INVALID);
        fit.u8(maxHr >= 0 ? maxHr : U8_INVALID);
        fit.u8(avgCad >= 0 ? avgCad : U8_INVALID);
        fit.u8(maxCad >= 0 ? maxCad : U8_INVALID);
        fit.u16(avgPower >= 0 ? avgPower : U16_INVALID);
        fit.u16(maxPower >= 0 ? maxPower : U16_INVALID);
    }

    /** {maxHr, maxPower, maxCad, maxSpeedMmps} over samples with pm in (from, to]. -1 where absent. */
    private static int[] maxInWindow(List<double[]> samples, double from, double to) {
        int hr = -1, power = -1, cad = -1, mmps = -1;
        for (double[] s : samples) {
            if (s[0] > from && s[0] <= to + 1e-6) {
                if (s[1] >= 0) hr = Math.max(hr, (int) s[1]);
                if (s[2] >= 0) power = Math.max(power, (int) s[2]);
                if (s[3] >= 0) mmps = Math.max(mmps, (int) Math.round(s[3] * 1000));
                if (s[4] >= 0) cad = Math.max(cad, (int) s[4]);
            }
        }
        return new int[] {hr, power, cad, mmps};
    }

    private static int countInWindow(List<JsonNode> strokes, double from, double to) {
        int c = 0;
        for (JsonNode n : strokes) {
            double pm = n.path("pmTime").asDouble(-1);
            if (pm > from && pm <= to + 1e-6) {
                c++;
            }
        }
        return c;
    }

    private static int strokeU16(JsonNode stroke, String field, int scale) {
        if (stroke == null || !stroke.hasNonNull(field)) {
            return U16_INVALID;
        }
        return (int) Math.round(stroke.get(field).asDouble() * scale);
    }

    private static int strokeMillis(JsonNode stroke, String field) {
        return strokeU16(stroke, field, 1000);
    }

    private static int strokeCenti(JsonNode stroke, String field) {
        return strokeU16(stroke, field, 100);
    }

    private static long fit(long startUnix, double elapsedS) {
        return Math.max(0, startUnix + Math.round(elapsedS) - TS_EPOCH);
    }

    private static double avg(double sum, int count) {
        return count > 0 ? sum / count : -1;
    }

    private static long pmKey(double pm) {
        return Math.round(pm * 100);
    }

    private static double num(JsonNode n, String field, double def) {
        return n != null && n.hasNonNull(field) ? n.get(field).asDouble() : def;
    }

    private static String text(JsonNode n, String field, String def) {
        return n != null && n.hasNonNull(field) ? n.get(field).asText() : def;
    }

    private long resolveStart(JsonNode session, List<JsonNode> general) {
        Long unix = parseEpoch(text(session, "startedAt", null));
        if (unix == null && !general.isEmpty() && general.get(0).hasNonNull("hostTime")) {
            unix = parseEpoch(general.get(0).get("hostTime").asText());
        }
        return unix != null ? unix : TS_EPOCH;
    }

    private static Long parseEpoch(String iso) {
        if (iso == null) {
            return null;
        }
        try {
            return Instant.parse(iso).getEpochSecond();
        } catch (Exception e) {
            return null;
        }
    }

    private List<double[]> sortedPmValues(Path file, String valueField) throws IOException {
        List<double[]> out = new ArrayList<>();
        for (JsonNode n : readLines(file)) {
            if (n.hasNonNull("pmTime") && n.hasNonNull(valueField)) {
                out.add(new double[] {n.get("pmTime").asDouble(), n.get(valueField).asDouble()});
            }
        }
        out.sort(Comparator.comparingDouble(a -> a[0]));
        return out;
    }

    private int lastInt(Path file, String field) throws IOException {
        int v = U16_INVALID;
        for (JsonNode n : readLines(file)) {
            if (n.hasNonNull(field)) {
                v = n.get(field).asInt();
            }
        }
        return v;
    }

    private static JsonNode readJson(Path p) throws IOException {
        return Files.exists(p) ? MAPPER.readTree(Files.readString(p)) : null;
    }

    private static List<JsonNode> readLines(Path f) throws IOException {
        if (!Files.exists(f)) {
            return List.of();
        }
        List<JsonNode> out = new ArrayList<>();
        for (String line : Files.readAllLines(f)) {
            if (!line.isBlank()) {
                out.add(MAPPER.readTree(line));
            }
        }
        return out;
    }
}
