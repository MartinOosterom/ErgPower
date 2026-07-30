package work.zing.ergpower.pm5.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The optional single-athlete profile (change {@code rower-profile}), bound from {@code ergpower.athlete.*}
 * — typically the git-ignored local config. Every field is optional; unset fields simply omit the values
 * they would derive (watts/kg, HR zones, goal framing).
 */
@ConfigurationProperties(prefix = "ergpower.athlete")
public record AthleteProperties(
        Double weightKg, Integer age, String sex, Integer hrMax, Integer hrRest, String goal) {

    /** watts/kg for a session's average power, or {@code null} when no weight is set. */
    public Double wattsPerKg(double avgPowerW) {
        return (weightKg != null && weightKg > 0) ? avgPowerW / weightKg : null;
    }

    /** HR max: the configured value, else the {@code 220 − age} estimate, else {@code null}. */
    public Integer resolvedHrMax() {
        if (hrMax != null && hrMax > 0) {
            return hrMax;
        }
        return (age != null && age > 0) ? 220 - age : null;
    }

    /** The training zone (Z1–Z5) for a heart rate as a fraction of HR max, or {@code null} when unknown. */
    public String hrZone(int bpm) {
        Integer max = resolvedHrMax();
        if (max == null || max <= 0 || bpm <= 0) {
            return null;
        }
        double pct = 100.0 * bpm / max;
        if (pct < 60) {
            return "Z1";
        } else if (pct < 70) {
            return "Z2";
        } else if (pct < 80) {
            return "Z3";
        } else if (pct < 90) {
            return "Z4";
        }
        return "Z5";
    }

    /** The training goal, or {@code null} when unset/blank. */
    public String goalOrNull() {
        return (goal != null && !goal.isBlank()) ? goal.strip() : null;
    }
}
