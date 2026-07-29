package work.zing.ergpower.pm5.analysis;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import work.zing.ergpower.api.model.CurveBand;
import work.zing.ergpower.api.model.FeatureStat;
import work.zing.ergpower.api.model.FeatureTrend;
import work.zing.ergpower.api.model.ScoreMetric;
import work.zing.ergpower.api.model.SessionAnalysis;
import work.zing.ergpower.api.model.TechniqueFlag;
import work.zing.ergpower.api.model.TrendPoint;
import work.zing.ergpower.pm5.config.ErgPowerBleProperties;

/**
 * Deterministic technique analysis of a stored session's force curves — no model, no network. From each
 * stroke's {@code forcesN[]} it extracts Kleshnev shape features (peak, peak position, catch gradient,
 * finish plateau, mean/max ratio, hump index), aggregates them (mean±band curve, per-feature
 * consistency, drift trends), scores the session against published target windows, and flags common
 * faults. Grounding: Kleshnev, <i>Biomechanics of Rowing</i> (Table 9.2); BioRow; row2k; British Rowing.
 */
@Component
public class TechniqueAnalyzer {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int BINS = 50; // resample resolution along the normalized drive

    private final Path storageDir;

    @Autowired
    public TechniqueAnalyzer(ErgPowerBleProperties ble) {
        this(Path.of(ble.storage().dir()));
    }

    TechniqueAnalyzer(Path storageDir) {
        this.storageDir = storageDir;
    }

    public SessionAnalysis analyze(String id) throws IOException {
        Path dir = storageDir.resolve(id);
        if (!Files.isDirectory(dir)) {
            throw new NoSuchElementException(id);
        }

        List<double[]> curves = new ArrayList<>();
        List<Integer> strokeNums = new ArrayList<>();
        for (JsonNode n : readLines(dir.resolve("force-curve.ndjson"))) {
            JsonNode arr = n.get("forcesN");
            if (arr == null || !arr.isArray() || arr.size() < 2) {
                continue;
            }
            double[] f = new double[arr.size()];
            for (int i = 0; i < f.length; i++) {
                f[i] = arr.get(i).asDouble();
            }
            curves.add(f);
            strokeNums.add(n.hasNonNull("strokeCount") ? n.get("strokeCount").asInt() : curves.size() - 1);
        }
        if (curves.isEmpty()) {
            return new SessionAnalysis(id, 0, false); // "no curve data" state
        }

        int m = curves.size();
        double[] peak = new double[m], peakPos = new double[m], catchG = new double[m];
        double[] plateau = new double[m], mmr = new double[m];
        double[] humpD = new double[m];
        double[][] resampled = new double[m][];
        for (int s = 0; s < m; s++) {
            double[] f = curves.get(s);
            Feat ft = features(f);
            peak[s] = ft.peak;
            peakPos[s] = ft.peakPos;
            catchG[s] = ft.catchGrad;
            plateau[s] = ft.plateau;
            mmr[s] = ft.meanMax;
            humpD[s] = ft.hump;
            resampled[s] = resample(f, BINS);
        }

        // Mean ± 1σ curve over the normalized drive.
        List<CurveBand> band = new ArrayList<>();
        for (int b = 0; b < BINS; b++) {
            double[] col = new double[m];
            for (int s = 0; s < m; s++) {
                col[s] = resampled[s][b];
            }
            double mean = mean(col), sd = sd(col, mean);
            band.add(new CurveBand(bd((double) b / (BINS - 1)), bd(mean), bd(mean - sd), bd(mean + sd)));
        }
        // Each stroke's resampled curve (for the heatmap / overlay).
        List<List<BigDecimal>> curveList = new ArrayList<>();
        for (double[] r : resampled) {
            List<BigDecimal> row = new ArrayList<>(r.length);
            for (double v : r) {
                row.add(bd(v));
            }
            curveList.add(row);
        }

        List<FeatureStat> features = List.of(
                stat("peakForce", "Peak force", "N", peak),
                stat("peakPosition", "Peak position", "%", peakPos),
                stat("catchGradient", "Catch gradient", "%", catchG),
                stat("finishPlateau", "Finish plateau", "%", plateau),
                stat("meanMaxRatio", "Mean/max ratio", "", mmr),
                stat("humpIndex", "Hump index", "", humpD));

        List<ScoreMetric> scorecard = List.of(
                metric("catchGradient", "Catch gradient", mean(catchG), "%", null, 17.0,
                        "Reach 70% of peak within the first 17% of the drive — a legs-driven catch."),
                metric("peakPosition", "Peak position", mean(peakPos), "%", null, 40.0,
                        "Peak in the first 40% of the drive (front-loaded); >55% is back/arm-dominant."),
                metric("finishPlateau", "Finish plateau", mean(plateau), "%", 28.0, 40.0,
                        "Hold force through the finish — a plateau 28–40% of the drive."),
                metric("meanMaxRatio", "Mean / max ratio", mean(mmr), "", null, null,
                        "Higher = a fatter curve = more work per stroke (no hard target)."));

        List<FeatureTrend> trends = List.of(
                trend("peakForce", "Peak force", "N", strokeNums, peak),
                trend("peakPosition", "Peak position", "%", strokeNums, peakPos),
                trend("catchGradient", "Catch gradient", "%", strokeNums, catchG));

        List<TechniqueFlag> flags = new ArrayList<>();
        int disconnected = 0;
        for (double h : humpD) {
            if (h > 1) {
                disconnected++;
            }
        }
        if ((double) disconnected / m > 0.2) {
            flags.add(flag("disconnection", "warn", disconnected
                    + " strokes show a double peak — the drive is disconnecting (legs/back/arms not linked).", disconnected));
        }
        if (mean(peakPos) > 55) {
            flags.add(flag("late_peak", "warn", "Peak force is late in the drive (" + r0(mean(peakPos))
                    + "%) — back/arm-dominant or poor sequencing.", null));
        }
        if (mean(catchG) > 25) {
            flags.add(flag("soft_catch", "warn", "Slow force build at the catch (" + r0(mean(catchG))
                    + "%) — connect the legs earlier.", null));
        }
        if (mean(plateau) < 20) {
            flags.add(flag("collapsing_finish", "warn", "Force collapses after the peak (plateau "
                    + r0(mean(plateau)) + "%) — stay connected through the finish.", null));
        }
        if (cv(peakPos) > 0.15) {
            flags.add(flag("inconsistent", "info", "Stroke shape varies a lot (peak-position CV "
                    + r2(cv(peakPos)) + ") — work on repeatability.", null));
        }
        double drift = quarterDrift(peakPos);
        if (drift > 8) {
            flags.add(flag("fatigue_drift", "info", "Peak position drifted " + r0(drift)
                    + "% later from start to end — technique fading under fatigue.", null));
        }

        return new SessionAnalysis(id, m, true).bins(BINS)
                .scorecard(scorecard).meanCurve(band).curves(curveList).features(features).trends(trends).flags(flags);
    }

    private record Feat(double peak, double peakPos, double catchGrad, double plateau, double meanMax, int hump) {}

    /** Shape features from one force curve (positions expressed as % of the drive). */
    private static Feat features(double[] f) {
        int n = f.length;
        double peak = 0;
        int pi = 0;
        double sum = 0;
        for (int i = 0; i < n; i++) {
            sum += f[i];
            if (f[i] > peak) {
                peak = f[i];
                pi = i;
            }
        }
        double thr = 0.7 * peak;
        int ci = 0;
        for (int i = 0; i < n; i++) {
            if (f[i] >= thr) {
                ci = i;
                break;
            }
        }
        int plateau = 0;
        for (int i = pi + 1; i < n; i++) {
            if (f[i] >= thr) {
                plateau++;
            }
        }
        double denom = n > 1 ? n - 1 : 1;
        double meanMax = peak > 0 ? sum / n / peak : 0;
        return new Feat(peak, pi / denom * 100, ci / denom * 100, plateau / denom * 100, meanMax, humps(f, peak));
    }

    /** Count prominent local maxima (≥50% of peak, prominence ≥10% of peak); ≥1 for any real curve. */
    private static int humps(double[] f, double peak) {
        int count = 0;
        double minHeight = 0.5 * peak, prom = 0.1 * peak;
        for (int i = 1; i < f.length - 1; i++) {
            if (f[i] >= f[i - 1] && f[i] > f[i + 1] && f[i] >= minHeight) {
                double left = f[i], right = f[i];
                for (int j = i - 1; j >= 0 && f[j] < f[i]; j--) {
                    left = Math.min(left, f[j]);
                }
                for (int j = i + 1; j < f.length && f[j] < f[i]; j++) {
                    right = Math.min(right, f[j]);
                }
                if (Math.min(f[i] - left, f[i] - right) >= prom) {
                    count++;
                }
            }
        }
        return Math.max(count, 1);
    }

    private static double[] resample(double[] f, int bins) {
        int n = f.length;
        double[] out = new double[bins];
        for (int i = 0; i < bins; i++) {
            double pos = n > 1 ? (double) i / (bins - 1) * (n - 1) : 0;
            int lo = (int) Math.floor(pos);
            int hi = Math.min(lo + 1, n - 1);
            out[i] = f[lo] + (f[hi] - f[lo]) * (pos - lo);
        }
        return out;
    }

    private static ScoreMetric metric(String key, String label, double value, String unit, Double min, Double max, String note) {
        boolean hasTarget = min != null || max != null;
        Boolean pass = hasTarget ? (min == null || value >= min) && (max == null || value <= max) : null;
        return new ScoreMetric(key, label).value(bd(value)).unit(unit)
                .targetMin(min == null ? null : bd(min)).targetMax(max == null ? null : bd(max)).pass(pass).note(note);
    }

    private static FeatureStat stat(String key, String label, String unit, double[] v) {
        double mean = mean(v);
        return new FeatureStat(key, label).unit(unit).avg(bd(mean)).cv(bd(cv(v)))
                .min(bd(min(v))).max(bd(max(v)));
    }

    private static FeatureTrend trend(String key, String label, String unit, List<Integer> strokes, double[] v) {
        List<TrendPoint> pts = new ArrayList<>(v.length);
        for (int i = 0; i < v.length; i++) {
            pts.add(new TrendPoint(strokes.get(i), bd(v[i])));
        }
        return new FeatureTrend(key, label, pts).unit(unit);
    }

    private static TechniqueFlag flag(String code, String severity, String message, Integer count) {
        return new TechniqueFlag(code, TechniqueFlag.SeverityEnum.fromValue(severity), message).count(count);
    }

    private static double mean(double[] v) {
        double s = 0;
        for (double x : v) {
            s += x;
        }
        return v.length == 0 ? 0 : s / v.length;
    }

    private static double sd(double[] v, double mean) {
        double s = 0;
        for (double x : v) {
            s += (x - mean) * (x - mean);
        }
        return v.length == 0 ? 0 : Math.sqrt(s / v.length);
    }

    private static double cv(double[] v) {
        double mean = mean(v);
        return mean == 0 ? 0 : sd(v, mean) / mean;
    }

    private static double min(double[] v) {
        double r = Double.MAX_VALUE;
        for (double x : v) {
            r = Math.min(r, x);
        }
        return v.length == 0 ? 0 : r;
    }

    private static double max(double[] v) {
        double r = -Double.MAX_VALUE;
        for (double x : v) {
            r = Math.max(r, x);
        }
        return v.length == 0 ? 0 : r;
    }

    /** Mean of the last quarter minus the first quarter — a simple drift measure. */
    private static double quarterDrift(double[] v) {
        int q = v.length / 4;
        if (q < 1) {
            return 0;
        }
        double first = 0, last = 0;
        for (int i = 0; i < q; i++) {
            first += v[i];
            last += v[v.length - 1 - i];
        }
        return (last - first) / q;
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    private static String r0(double v) {
        return String.valueOf(Math.round(v));
    }

    private static String r2(double v) {
        return bd(v).toPlainString();
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
