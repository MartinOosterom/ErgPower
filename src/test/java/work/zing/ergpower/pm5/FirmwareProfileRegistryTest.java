package work.zing.ergpower.pm5;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import work.zing.ergpower.pm5.firmware.FirmwareProfileRegistry;

/**
 * Selection precedence and fingerprint matching for {@link FirmwareProfileRegistry} — no hardware.
 */
class FirmwareProfileRegistryTest {

    private final FirmwareProfileRegistry registry = new FirmwareProfileRegistry();

    @Test
    void defaultIsNewestProfile() {
        assertEquals("current-pm5-2026", registry.defaultProfile().id());
    }

    @Test
    void configOverrideByAliasWins() {
        assertEquals("reference-rev1.30", registry.byId("reference").orElseThrow().id());
        assertEquals("current-pm5-2026", registry.byId("current").orElseThrow().id());
        assertTrue(registry.byId("auto").isEmpty());
        assertTrue(registry.byId(null).isEmpty());
    }

    @Test
    void fingerprintPicksProfileFromObservedLengths() {
        assertEquals("current-pm5-2026",
                registry.byFingerprint(Map.of(0x33, 20, 0x35, 20)).orElseThrow().id());
        assertEquals("reference-rev1.30",
                registry.byFingerprint(Map.of(0x33, 18, 0x35, 18)).orElseThrow().id());
    }

    @Test
    void selectPrecedenceOverrideThenFirmwareThenDefault() {
        // override wins
        assertEquals("reference-rev1.30", registry.select("reference", "whatever").id());
        // no override, unknown firmware -> default (newest)
        assertEquals("current-pm5-2026", registry.select(null, "unrecognised-fw").id());
        assertEquals("current-pm5-2026", registry.select("auto", null).id());
    }
}
