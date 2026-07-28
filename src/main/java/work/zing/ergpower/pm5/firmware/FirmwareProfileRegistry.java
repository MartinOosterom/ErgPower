package work.zing.ergpower.pm5.firmware;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Holds the known {@link FirmwareProfile}s and selects one per connection (design decision D3),
 * most-specific-first:
 * <ol>
 *   <li>an explicit config override (by id/alias),</li>
 *   <li>a match on the reported firmware revision string ({@link FirmwareProfile#claims}),</li>
 *   <li>a structural fingerprint of observed characteristic lengths,</li>
 *   <li>otherwise the newest profile (the default).</li>
 * </ol>
 * Profiles are ordered newest-first; the first is the default.
 */
public final class FirmwareProfileRegistry {

    private final List<FirmwareProfile> profiles;

    public FirmwareProfileRegistry() {
        this(List.of(new CurrentPm5(), new ReferenceRev130()));
    }

    public FirmwareProfileRegistry(List<FirmwareProfile> profiles) {
        this.profiles = List.copyOf(profiles);
    }

    public List<FirmwareProfile> all() {
        return profiles;
    }

    public FirmwareProfile defaultProfile() {
        return profiles.get(0);
    }

    /** Match by config value: a profile id, or a substring alias like {@code current} / {@code reference}. */
    public Optional<FirmwareProfile> byId(String id) {
        if (id == null || id.isBlank() || id.equalsIgnoreCase("auto")) {
            return Optional.empty();
        }
        String needle = id.toLowerCase();
        return profiles.stream()
                .filter(p -> p.id().equalsIgnoreCase(id) || p.id().toLowerCase().contains(needle))
                .findFirst();
    }

    /** Match by the PM5 firmware revision string. */
    public Optional<FirmwareProfile> byFirmware(String firmware) {
        if (firmware == null || firmware.isBlank()) {
            return Optional.empty();
        }
        return profiles.stream().filter(p -> p.claims(firmware)).findFirst();
    }

    /**
     * Match by observed characteristic lengths: the profile that agrees with every observed length it
     * declares, scoring by how many it matches. Returns empty if none is contradiction-free.
     */
    public Optional<FirmwareProfile> byFingerprint(Map<Integer, Integer> observedLengths) {
        FirmwareProfile best = null;
        int bestScore = 0;
        for (FirmwareProfile p : profiles) {
            int score = 0;
            boolean contradiction = false;
            for (Map.Entry<Integer, Integer> e : observedLengths.entrySet()) {
                int expected = p.expectedLength(e.getKey());
                if (expected < 0) {
                    continue;
                }
                if (expected == e.getValue()) {
                    score++;
                } else {
                    contradiction = true;
                    break;
                }
            }
            if (!contradiction && score > bestScore) {
                best = p;
                bestScore = score;
            }
        }
        return Optional.ofNullable(best);
    }

    /** Selection at connect (firmware known, no frames yet): override → firmware → default. */
    public FirmwareProfile select(String overrideId, String firmware) {
        return byId(overrideId)
                .or(() -> byFirmware(firmware))
                .orElseGet(this::defaultProfile);
    }
}
