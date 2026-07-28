package work.zing.ergpower.pm5.storage;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import work.zing.ergpower.pm5.event.AdditionalSplitData;
import work.zing.ergpower.pm5.event.AdditionalStatus1;
import work.zing.ergpower.pm5.event.AdditionalStatus2;
import work.zing.ergpower.pm5.event.AdditionalStrokeData;
import work.zing.ergpower.pm5.event.ForceCurve;
import work.zing.ergpower.pm5.event.GeneralStatus;
import work.zing.ergpower.pm5.event.SplitData;
import work.zing.ergpower.pm5.event.Pm5Event;
import work.zing.ergpower.pm5.event.RawFrame;
import work.zing.ergpower.pm5.event.StrokeData;

/**
 * Writes one rowing session into its own folder as a faithful, per-characteristic mirror of the
 * event stream (spec: {@code session-storage}).
 *
 * <p>Each event type gets its own append-only NDJSON file; every record carries the join keys
 * ({@code pmTime} + {@code hostTime}, and {@code strokeCount} for per-stroke records) so any subset
 * of files recombines by time and/or stroke. On {@link #finish()} it writes a {@code session.json}
 * manifest (provenance + char→file map) and a {@code summary.json} of session totals.
 *
 * <p>Not thread-safe: one writer per session, fed from a single subscriber.
 */
public final class SessionStorageWriter implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path dir;
    private final SessionMeta meta;
    private final Map<String, BufferedWriter> writers = new LinkedHashMap<>();
    private final Map<String, String> files = new LinkedHashMap<>(); // filename -> event type

    private Instant startHost;
    private String deviceOverride;
    private String firmware;
    private String profileId;
    private String profileNote;
    private double maxDistance;
    private double lastPmTime;
    private int maxStrokeCount = -1;
    private long powerSum;
    private int powerCount;
    private int peakPower;
    private int forceCurveCount;
    private long eventCount;
    private boolean finished;

    public SessionStorageWriter(Path dir, SessionMeta meta) throws IOException {
        this.dir = dir;
        this.meta = meta;
        Files.createDirectories(dir);
    }

    /** Route one event to its per-characteristic file and update running totals. */
    public void write(Pm5Event event) throws IOException {
        if (startHost == null) {
            startHost = event.hostTime();
        }
        eventCount++;
        lastPmTime = Math.max(lastPmTime, Double.isNaN(event.pmTime()) ? lastPmTime : event.pmTime());

        Map<String, Object> rec = new LinkedHashMap<>();
        switch (event) {
            case GeneralStatus g -> {
                putKeys(rec, "general-status", g.pmTime(), g.hostTime(), null);
                rec.put("distanceM", round2(g.distanceMeters()));
                rec.put("workoutType", g.workoutType());
                rec.put("intervalType", g.intervalType());
                rec.put("workoutState", g.workoutState());
                rec.put("rowingState", g.rowingState());
                rec.put("strokeState", g.strokeState());
                rec.put("workoutDurationType", g.workoutDurationType());
                rec.put("targetTimeS", g.targetTimeSeconds() == null ? null : round2(g.targetTimeSeconds()));
                rec.put("targetDistanceM", g.targetDistanceMeters());
                rec.put("dragFactor", g.dragFactor());
                maxDistance = Math.max(maxDistance, g.distanceMeters());
                emit("status-general.ndjson", "general-status", rec);
            }
            case AdditionalStatus1 a1 -> {
                putKeys(rec, "additional-status1", a1.pmTime(), a1.hostTime(), null);
                rec.put("speedMps", round2(a1.speedMetersPerSecond()));
                rec.put("strokeRate", a1.strokeRate());
                rec.put("heartRateBpm", a1.heartRateBpm()); // null when no belt
                rec.put("currentPaceS", round2(a1.currentPaceSeconds()));
                rec.put("avgPaceS", round2(a1.avgPaceSeconds()));
                rec.put("restDistanceM", a1.restDistanceMeters());
                rec.put("restTimeS", round2(a1.restTimeSeconds()));
                rec.put("ergMachineType", a1.ergMachineType());
                emit("status-additional1.ndjson", "additional-status1", rec);
            }
            case AdditionalStatus2 a2 -> {
                putKeys(rec, "additional-status2", a2.pmTime(), a2.hostTime(), null);
                rec.put("intervalCount", a2.intervalCount());
                rec.put("totalCalories", a2.totalCalories());
                rec.put("splitAvgPaceS", round2(a2.splitAvgPaceSeconds()));
                rec.put("splitAvgPowerW", a2.splitAvgPowerWatts());
                emit("status-additional2.ndjson", "additional-status2", rec);
            }
            case StrokeData s -> {
                putKeys(rec, "stroke", s.pmTime(), s.hostTime(), s.strokeCount());
                rec.put("distanceM", round2(s.distanceMeters()));
                rec.put("driveLengthM", round2(s.driveLengthMeters()));
                rec.put("driveTimeS", round2(s.driveTimeSeconds()));
                rec.put("recoveryTimeS", round2(s.recoveryTimeSeconds()));
                rec.put("strokeDistanceM", round2(s.strokeDistanceMeters()));
                rec.put("peakDriveForceN", round2(s.peakDriveForceNewtons()));
                rec.put("avgDriveForceN", round2(s.avgDriveForceNewtons()));
                maxStrokeCount = Math.max(maxStrokeCount, s.strokeCount());
                emit("stroke.ndjson", "stroke", rec);
            }
            case AdditionalStrokeData a -> {
                putKeys(rec, "stroke-additional", a.pmTime(), a.hostTime(), a.strokeCount());
                rec.put("strokePowerW", a.strokePowerWatts());
                rec.put("strokeCaloriesPerHour", a.strokeCaloriesPerHour());
                rec.put("projectedWorkTimeS", round2(a.projectedWorkTimeSeconds()));
                rec.put("projectedWorkDistanceM", a.projectedWorkDistanceMeters());
                peakPower = Math.max(peakPower, a.strokePowerWatts());
                powerSum += a.strokePowerWatts();
                powerCount++;
                maxStrokeCount = Math.max(maxStrokeCount, a.strokeCount());
                emit("stroke-additional.ndjson", "stroke-additional", rec);
            }
            case SplitData s -> {
                putKeys(rec, "split", s.pmTime(), s.hostTime(), null);
                rec.put("distanceM", round2(s.distanceMeters()));
                rec.put("splitTimeS", round2(s.splitTimeSeconds()));
                rec.put("splitDistanceM", s.splitDistanceMeters());
                rec.put("restTimeS", s.restTimeSeconds());
                rec.put("restDistanceM", s.restDistanceMeters());
                rec.put("splitType", s.splitType());
                rec.put("splitNumber", s.splitNumber());
                emit("split.ndjson", "split", rec);
            }
            case AdditionalSplitData s -> {
                putKeys(rec, "split-additional", s.pmTime(), s.hostTime(), null);
                rec.put("avgStrokeRate", s.avgStrokeRate());
                rec.put("workHeartRateBpm", s.workHeartRateBpm());
                rec.put("restHeartRateBpm", s.restHeartRateBpm());
                rec.put("avgPaceS", round2(s.avgPaceSeconds()));
                rec.put("totalCalories", s.totalCalories());
                rec.put("avgCaloriesPerHour", s.avgCaloriesPerHour());
                rec.put("speedMps", round2(s.speedMetersPerSecond()));
                rec.put("powerW", s.powerWatts());
                emit("split-additional.ndjson", "split-additional", rec);
            }
            case ForceCurve f -> {
                putKeys(rec, "force-curve", f.pmTime(), f.hostTime(), f.strokeCount());
                rec.put("points", f.length());
                rec.put("forcesN", round1(f.forcesNewtons()));
                forceCurveCount++;
                emit("force-curve.ndjson", "force-curve", rec);
            }
            case RawFrame r -> {
                String id = String.format("0x%02x", r.characteristicId());
                putKeys(rec, "raw", r.pmTime(), r.hostTime(), null);
                rec.put("characteristicId", id);
                rec.put("bytes", toHex(r.data()));
                emit("raw-" + id + ".ndjson", "raw:" + id, rec);
            }
        }
    }

    /**
     * Append one raw bridge frame line to {@code raw.ndjson} (flushed immediately) so the live session
     * can be re-decoded later. No-op-safe to call concurrently with {@link #write}.
     */
    public synchronized void writeRaw(String frameLine) throws IOException {
        BufferedWriter w = writers.computeIfAbsent("raw.ndjson", this::openWriter);
        files.putIfAbsent("raw.ndjson", "raw-frames");
        w.write(frameLine);
        w.newLine();
        w.flush();
    }

    /** Override the manifest device (e.g. the name the bridge actually connected to). Ignored if blank. */
    public void setDevice(String device) {
        if (device != null && !device.isBlank()) {
            this.deviceOverride = device;
        }
    }

    /** Record how this session was decoded: firmware revision, profile id, and any fingerprint note. */
    public void setDecodeProvenance(String firmware, String profileId, String note) {
        this.firmware = firmware;
        this.profileId = profileId;
        this.profileNote = note;
    }

    /** Write the manifest + summary and close all files. Idempotent. */
    public void finish() throws IOException {
        if (finished) {
            return;
        }
        finished = true;
        for (BufferedWriter w : writers.values()) {
            w.flush();
        }

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("sessionId", dir.getFileName().toString());
        manifest.put("startedAt", startHost == null ? null : startHost.toString());
        manifest.put("source", meta.source());
        manifest.put("device", deviceOverride != null ? deviceOverride : meta.deviceName());
        manifest.put("firmware", firmware != null ? firmware : meta.firmware());
        manifest.put("profileId", profileId);
        if (profileNote != null) {
            manifest.put("profileNote", profileNote);
        }
        manifest.put("appVersion", meta.appVersion());
        manifest.put("decoderNote",
                "field offsets reverse-engineered from real capture; force curve = rev-1.30 0x003D; see design.md");
        manifest.put("events", eventCount);
        manifest.put("files", files);
        writeJson("session.json", manifest);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("strokes", maxStrokeCount + 1);
        summary.put("distanceM", round2(maxDistance));
        summary.put("durationS", round2(lastPmTime));
        summary.put("avgPowerW", powerCount == 0 ? 0 : Math.round((double) powerSum / powerCount));
        summary.put("peakPowerW", peakPower);
        summary.put("forceCurves", forceCurveCount);
        writeJson("summary.json", summary);

        close();
    }

    @Override
    public void close() throws IOException {
        IOException first = null;
        for (BufferedWriter w : writers.values()) {
            try {
                w.close();
            } catch (IOException e) {
                if (first == null) {
                    first = e;
                }
            }
        }
        writers.clear();
        if (first != null) {
            throw first;
        }
    }

    private void emit(String file, String type, Map<String, Object> rec) throws IOException {
        files.putIfAbsent(file, type);
        BufferedWriter w = writers.computeIfAbsent(file, this::openWriter);
        w.write(MAPPER.writeValueAsString(rec));
        w.newLine();
        w.flush(); // crash-safe: a long piece keeps all data received before an interruption
    }

    private BufferedWriter openWriter(String file) {
        try {
            return Files.newBufferedWriter(dir.resolve(file));
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private void writeJson(String file, Object value) throws IOException {
        Files.writeString(dir.resolve(file), MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value));
    }

    private static void putKeys(Map<String, Object> rec, String type, double pmTime, Instant hostTime, Integer strokeCount) {
        rec.put("type", type);
        rec.put("pmTime", Double.isNaN(pmTime) ? null : round2(pmTime));
        rec.put("hostTime", hostTime.toString());
        if (strokeCount != null) {
            rec.put("strokeCount", strokeCount);
        }
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static double[] round1(double[] values) {
        double[] out = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = Math.round(values[i] * 10.0) / 10.0;
        }
        return out;
    }

    private static String toHex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte value : b) {
            sb.append(Character.forDigit((value >> 4) & 0xF, 16)).append(Character.forDigit(value & 0xF, 16));
        }
        return sb.toString();
    }
}
