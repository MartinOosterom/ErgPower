package work.zing.ergpower.pm5.export;

import static work.zing.ergpower.pm5.export.Fit.ENUM;
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
 * Builds a Garmin .FIT rowing activity from a stored session, recombining the per-characteristic NDJSON
 * by {@code pmTime} (design D2/D3): one FIT record per status sample (timestamp, distance, speed,
 * power, heart rate, stroke cadence), one lap per split, and a session summary. Units are mapped to
 * FIT's scaled integers; timestamps are the session start plus each sample's elapsed time in the FIT
 * epoch.
 */
@Component
public class SessionFitExporter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Global message numbers.
    private static final int MSG_FILE_ID = 0, MSG_SESSION = 18, MSG_LAP = 19, MSG_RECORD = 20,
            MSG_EVENT = 21, MSG_ACTIVITY = 34;
    // Local message types (definition slots).
    private static final int L_RECORD = 0, L_FILE_ID = 1, L_EVENT = 2, L_LAP = 3, L_SESSION = 4, L_ACTIVITY = 5;
    private static final int SPORT_ROWING = 15;

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

        // Index additional-status-1 (speed/rate/HR) by pmTime; sort stroke power by pmTime.
        Map<Long, JsonNode> add1 = new HashMap<>();
        for (JsonNode n : readLines(dir.resolve("status-additional1.ndjson"))) {
            if (n.hasNonNull("pmTime")) {
                add1.put(pmKey(n.get("pmTime").asDouble()), n);
            }
        }
        List<double[]> powers = new ArrayList<>();
        for (JsonNode n : readLines(dir.resolve("stroke-additional.ndjson"))) {
            if (n.hasNonNull("pmTime") && n.hasNonNull("strokePowerW")) {
                powers.add(new double[] {n.get("pmTime").asDouble(), n.get("strokePowerW").asInt()});
            }
        }
        powers.sort(Comparator.comparingDouble(a -> a[0]));

        Fit fit = new Fit();

        // --- file_id ---
        fit.definition(L_FILE_ID, MSG_FILE_ID, new int[][] {
                {0, 1, ENUM}, {1, 2, UINT16}, {2, 2, UINT16}, {4, 4, UINT32}});
        fit.data(L_FILE_ID);
        fit.u8(4);                 // type = activity
        fit.u16(255);              // manufacturer = development
        fit.u16(0);                // product
        fit.u32(fit(startUnix, 0)); // time_created

        // --- event: timer start ---
        int[][] eventFields = {{253, 4, UINT32}, {0, 1, ENUM}, {1, 1, ENUM}};
        fit.definition(L_EVENT, MSG_EVENT, eventFields);
        fit.data(L_EVENT);
        fit.u32(fit(startUnix, 0));
        fit.u8(0); // event = timer
        fit.u8(0); // event_type = start

        // --- records ---
        fit.definition(L_RECORD, MSG_RECORD, new int[][] {
                {253, 4, UINT32}, {5, 4, UINT32}, {6, 2, UINT16}, {7, 2, UINT16}, {3, 1, UINT8}, {4, 1, UINT8}});

        double speedSum = 0, speedMax = 0;
        int speedCount = 0, hrSum = 0, hrMax = 0, hrCount = 0, cadSum = 0, cadMax = 0, cadCount = 0;
        double lastPm = 0, lastDistance = 0;
        int pi = 0, curPower = -1;

        for (JsonNode g : general) {
            if (!g.hasNonNull("pmTime")) {
                continue;
            }
            double pm = g.get("pmTime").asDouble();
            lastPm = pm;
            JsonNode a1 = add1.get(pmKey(pm));
            while (pi < powers.size() && powers.get(pi)[0] <= pm + 1e-6) {
                curPower = (int) powers.get(pi)[1];
                pi++;
            }

            double distanceM = g.path("distanceM").asDouble(-1);
            if (distanceM >= 0) {
                lastDistance = distanceM;
            }
            Integer hr = a1 != null && a1.hasNonNull("heartRateBpm") ? a1.get("heartRateBpm").asInt() : null;
            Integer spm = a1 != null && a1.hasNonNull("strokeRate") ? a1.get("strokeRate").asInt() : null;
            double mps = a1 != null && a1.hasNonNull("speedMps") ? a1.get("speedMps").asDouble() : -1;

            fit.data(L_RECORD);
            fit.u32(fit(startUnix, pm));                                        // timestamp
            fit.u32(distanceM >= 0 ? Math.round(distanceM * 100) : U32_INVALID); // distance (cm)
            fit.u16(mps >= 0 ? (int) Math.round(mps * 1000) : U16_INVALID);      // speed (mm/s)
            fit.u16(curPower >= 0 ? curPower : U16_INVALID);                     // power (W)
            fit.u8(hr != null ? hr : U8_INVALID);                               // heart_rate (bpm)
            fit.u8(spm != null ? spm : U8_INVALID);                             // cadence (spm)

            if (mps >= 0) { speedSum += mps; speedMax = Math.max(speedMax, mps); speedCount++; }
            if (hr != null) { hrSum += hr; hrMax = Math.max(hrMax, hr); hrCount++; }
            if (spm != null) { cadSum += spm; cadMax = Math.max(cadMax, spm); cadCount++; }
        }

        // --- event: timer stop ---
        double durationS = summary != null && summary.hasNonNull("durationS") ? summary.get("durationS").asDouble() : lastPm;
        double totalDistanceM = summary != null && summary.hasNonNull("distanceM") ? summary.get("distanceM").asDouble() : lastDistance;
        long endFit = fit(startUnix, durationS);
        fit.data(L_EVENT);
        fit.u32(endFit);
        fit.u8(0); // timer
        fit.u8(4); // stop_all

        // --- laps (one per split; synthesise one whole-session lap if there are none) ---
        fit.definition(L_LAP, MSG_LAP, new int[][] {
                {253, 4, UINT32}, {254, 2, UINT16}, {2, 4, UINT32}, {7, 4, UINT32}, {8, 4, UINT32}, {9, 4, UINT32}});
        List<JsonNode> splits = readLines(dir.resolve("split.ndjson"));
        int numLaps;
        if (splits.isEmpty()) {
            writeLap(fit, 0, fit(startUnix, 0), endFit, durationS, totalDistanceM);
            numLaps = 1;
        } else {
            int i = 0;
            for (JsonNode sp : splits) {
                double pm = sp.hasNonNull("pmTime") ? sp.get("pmTime").asDouble() : durationS;
                double splitTime = sp.path("splitTimeS").asDouble(0);
                double splitDist = sp.path("splitDistanceM").asDouble(0);
                long lapEnd = fit(startUnix, pm);
                writeLap(fit, i++, lapEnd - Math.round(splitTime), lapEnd, splitTime, splitDist);
            }
            numLaps = splits.size();
        }

        // --- session ---
        fit.definition(L_SESSION, MSG_SESSION, new int[][] {
                {253, 4, UINT32}, {254, 2, UINT16}, {2, 4, UINT32}, {7, 4, UINT32}, {8, 4, UINT32}, {9, 4, UINT32},
                {5, 1, ENUM}, {6, 1, ENUM}, {14, 2, UINT16}, {15, 2, UINT16}, {20, 2, UINT16}, {21, 2, UINT16},
                {16, 1, UINT8}, {17, 1, UINT8}, {18, 1, UINT8}, {19, 1, UINT8}, {26, 2, UINT16}});
        int avgPower = summary != null && summary.hasNonNull("avgPowerW") ? summary.get("avgPowerW").asInt() : U16_INVALID;
        int maxPower = summary != null && summary.hasNonNull("peakPowerW") ? summary.get("peakPowerW").asInt() : U16_INVALID;
        fit.data(L_SESSION);
        fit.u32(endFit);                                       // timestamp
        fit.u16(0);                                            // message_index
        fit.u32(fit(startUnix, 0));                            // start_time
        fit.u32(Math.round(durationS * 1000));                // total_elapsed_time
        fit.u32(Math.round(durationS * 1000));                // total_timer_time
        fit.u32(Math.round(totalDistanceM * 100));            // total_distance
        fit.u8(SPORT_ROWING);                                 // sport
        fit.u8(0);                                            // sub_sport
        fit.u16(avg(speedSum, speedCount) >= 0 ? (int) Math.round(avg(speedSum, speedCount) * 1000) : U16_INVALID); // avg_speed
        fit.u16(speedCount > 0 ? (int) Math.round(speedMax * 1000) : U16_INVALID); // max_speed
        fit.u16(avgPower);                                    // avg_power
        fit.u16(maxPower);                                    // max_power
        fit.u8(hrCount > 0 ? hrSum / hrCount : U8_INVALID);   // avg_heart_rate
        fit.u8(hrCount > 0 ? hrMax : U8_INVALID);             // max_heart_rate
        fit.u8(cadCount > 0 ? cadSum / cadCount : U8_INVALID); // avg_cadence
        fit.u8(cadCount > 0 ? cadMax : U8_INVALID);           // max_cadence
        fit.u16(numLaps);                                     // num_laps

        // --- activity ---
        fit.definition(L_ACTIVITY, MSG_ACTIVITY, new int[][] {
                {253, 4, UINT32}, {0, 4, UINT32}, {1, 2, UINT16}, {2, 1, ENUM}, {3, 1, ENUM}, {4, 1, ENUM}});
        fit.data(L_ACTIVITY);
        fit.u32(endFit);                        // timestamp
        fit.u32(Math.round(durationS * 1000));  // total_timer_time
        fit.u16(1);                             // num_sessions
        fit.u8(0);                              // type = manual
        fit.u8(26);                             // event = activity
        fit.u8(1);                              // event_type = stop

        return fit.toBytes();
    }

    private static void writeLap(Fit fit, int index, long startFit, long endFit, double elapsedS, double distanceM) {
        fit.data(L_LAP);
        fit.u32(endFit);                          // timestamp (lap end)
        fit.u16(index);                           // message_index
        fit.u32(startFit);                        // start_time
        fit.u32(Math.round(elapsedS * 1000));     // total_elapsed_time
        fit.u32(Math.round(elapsedS * 1000));     // total_timer_time
        fit.u32(Math.round(distanceM * 100));     // total_distance
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

    private long resolveStart(JsonNode session, List<JsonNode> general) {
        String started = session != null && session.hasNonNull("startedAt") ? session.get("startedAt").asText() : null;
        Long unix = parseEpoch(started);
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
