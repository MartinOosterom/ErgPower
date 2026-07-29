package work.zing.ergpower.pm5;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import work.zing.ergpower.pm5.api.DashboardStore;

/** {@link DashboardStore}: opaque JSON round-trip and the path-traversal name guard (design D6). */
class DashboardStoreTest {

    @TempDir
    Path dir;

    @Test
    void rejectsUnsafeNames() {
        DashboardStore store = new DashboardStore(dir.toString());
        for (String bad : List.of("../x", "a/b", "a\\b", "..", "", " ")) {
            assertThrows(IllegalArgumentException.class, () -> store.read(bad), () -> "should reject: " + bad);
        }
    }

    @Test
    void roundTripsConfigOpaquely() throws Exception {
        DashboardStore store = new DashboardStore(dir.toString());
        store.write("Race", Map.of("widgets", List.of(Map.of("type", "stat")), "customKey", "kept"));

        Map<String, Object> read = store.read("Race");
        assertEquals("kept", read.get("customKey"));
        assertEquals(List.of("Race"), store.list());

        assertTrue(store.delete("Race"));
        assertNull(store.read("Race"));
    }
}
