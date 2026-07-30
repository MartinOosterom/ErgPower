package work.zing.ergpower.pm5.coach;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Component;

import work.zing.ergpower.api.model.SessionIndexEntry;
import work.zing.ergpower.api.model.SessionIndexEntry.TargetTypeEnum;
import work.zing.ergpower.pm5.analysis.SessionIndex;

/**
 * Builds a compact, like-for-like <em>history</em> block for progress coaching (change
 * {@code coach-progress}): given the session being coached, it finds the athlete's recent
 * <b>same-type</b> sessions in the cross-session index and distils each technique metric's trend
 * (oldest → newest) plus a within-type performance line.
 *
 * <p>Two-lens comparison: technique-shape metrics compare across pieces, but pieces are still restricted
 * to the same workout type and a distance/time band so the comparison is fair. When there isn't enough
 * comparable history, {@link #block(String)} returns an empty string and the coach falls back to
 * single-session coaching.
 */
@Component
public class CoachHistory {

    /** Minimum comparable sessions (including the anchor) before a trend is asserted. */
    static final int MIN_COMPARABLE = 3;
    /** Cap the history rows so the prompt stays compact. */
    static final int MAX_SESSIONS = 6;
    private static final double DISTANCE_BAND = 0.25; // ±25% distance for distance pieces
    private static final double TIME_BAND = 0.20;     // ±20% target time for time pieces

    private static final List<String[]> METRICS = List.of(
            new String[] {"catchGradient", "catch gradient", "%"},
            new String[] {"peakPosition", "peak position", "%"},
            new String[] {"finishPlateau", "finish plateau", "%"},
            new String[] {"meanMaxRatio", "mean/max ratio", ""});

    private final SessionIndex index;

    public CoachHistory(SessionIndex index) {
        this.index = index;
    }

    /** The history block for the anchor session, or {@code ""} when there is no comparable history. */
    public String block(String anchorId) throws IOException {
        List<SessionIndexEntry> all = index.list(null, null, null, null, null); // newest first
        SessionIndexEntry anchor = all.stream().filter(e -> e.getId().equals(anchorId)).findFirst().orElse(null);
        if (anchor == null || anchor.getTargetType() == TargetTypeEnum.NONE) {
            return "";
        }
        List<SessionIndexEntry> comparable = all.stream()
                .filter(e -> e.getTargetType() == anchor.getTargetType())
                .filter(e -> e.getScores() != null && !e.getScores().isEmpty())
                .filter(e -> comparablePiece(anchor, e))
                .limit(MAX_SESSIONS)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (comparable.size() < MIN_COMPARABLE) {
            return "";
        }
        Collections.reverse(comparable); // oldest → newest

        String kind = anchor.getTargetType() == TargetTypeEnum.DISTANCE ? "distance" : "time";
        StringBuilder sb = new StringBuilder("\nRecent same-type history — ")
                .append(comparable.size()).append(" comparable ").append(kind)
                .append(" pieces (oldest → newest, the last is this session):\n");
        for (String[] m : METRICS) {
            String key = m[0], label = m[1], unit = m[2];
            List<String> values = new ArrayList<>();
            for (SessionIndexEntry e : comparable) {
                BigDecimal v = e.getScores().get(key);
                values.add(v != null ? trim(v) + unit : "?");
            }
            sb.append("- ").append(label).append(": ").append(String.join(" → ", values)).append("\n");
        }
        // Performance context (within this same type only).
        List<String> powers = new ArrayList<>();
        for (SessionIndexEntry e : comparable) {
            powers.add(e.getAvgPowerW() != null ? e.getAvgPowerW() + " W" : "?");
        }
        sb.append("- average power: ").append(String.join(" → ", powers)).append("\n");
        return sb.toString();
    }

    /** Same workout type plus a distance/time band, so only genuinely comparable pieces are trended. */
    private static boolean comparablePiece(SessionIndexEntry anchor, SessionIndexEntry e) {
        if (anchor.getTargetType() == TargetTypeEnum.DISTANCE) {
            return within(e.getDistanceM(), anchor.getDistanceM(), DISTANCE_BAND);
        }
        // time pieces: compare the target time, falling back to duration
        BigDecimal a = anchor.getTargetValue() != null ? anchor.getTargetValue() : anchor.getDurationS();
        BigDecimal b = e.getTargetValue() != null ? e.getTargetValue() : e.getDurationS();
        return within(b, a, TIME_BAND);
    }

    private static boolean within(BigDecimal v, BigDecimal ref, double band) {
        if (v == null || ref == null || ref.signum() == 0) {
            return false;
        }
        double r = ref.doubleValue();
        double d = Math.abs(v.doubleValue() - r) / r;
        return d <= band;
    }

    private static String trim(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }
}
