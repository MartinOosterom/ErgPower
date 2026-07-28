package work.zing.ergpower.pm5;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import work.zing.ergpower.pm5.source.ReplayPm5Source;
import work.zing.ergpower.pm5.storage.SessionMeta;
import work.zing.ergpower.pm5.storage.SessionStorage;

/**
 * End-to-end "capture → store": replays the real fixture through the decoder into a session folder,
 * then asserts the per-characteristic files, the manifest/summary, and — crucially — that the files
 * <b>recombine</b> on the join keys (every force curve's stroke matches an additional-stroke record).
 */
class StorageWriterTest {

    private static final Path FIXTURE =
            Path.of("ble-bridge/captures/2026-07-28_rowerg_432234859_289m_28strokes.ndjson");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void storesSessionAndFilesRecombine(@TempDir Path tmp) throws IOException {
        assumeTrue(Files.exists(FIXTURE), "capture fixture not present");

        Path session = tmp.resolve("session");
        SessionStorage.store(
                new ReplayPm5Source(FIXTURE),
                session,
                new SessionMeta("replay:" + FIXTURE.getFileName(), "PM5 432234859 Row", null, "0.0.1-SNAPSHOT"));

        // one NDJSON file per characteristic, faithful line counts
        assertEquals(295, lines(session.resolve("status-general.ndjson")));
        assertEquals(53, lines(session.resolve("stroke.ndjson")));
        assertEquals(28, lines(session.resolve("stroke-additional.ndjson")));
        assertEquals(26, lines(session.resolve("force-curve.ndjson")));
        // additional status now decoded (pace/spm/HR/power), not raw
        assertEquals(295, lines(session.resolve("status-additional1.ndjson")));
        assertEquals(295, lines(session.resolve("status-additional2.ndjson")));

        // manifest
        JsonNode manifest = MAPPER.readTree(Files.readString(session.resolve("session.json")));
        assertFalse(manifest.get("startedAt").isNull(), "manifest records start time");
        assertTrue(manifest.get("files").has("force-curve.ndjson"), "manifest lists the char→file map");
        assertEquals("PM5 432234859 Row", manifest.get("device").asText());

        // summary
        JsonNode summary = MAPPER.readTree(Files.readString(session.resolve("summary.json")));
        assertEquals(28, summary.get("strokes").asInt());
        assertEquals(206, summary.get("peakPowerW").asInt());
        assertEquals(26, summary.get("forceCurves").asInt());
        assertEquals(289.7, summary.get("distanceM").asDouble(), 0.2);

        // recombination: every force-curve stroke index joins to an additional-stroke record
        Set<Integer> strokeIndices = strokeCounts(session.resolve("stroke-additional.ndjson"));
        Set<Integer> curveStrokes = strokeCounts(session.resolve("force-curve.ndjson"));
        assertFalse(curveStrokes.isEmpty());
        assertTrue(strokeIndices.containsAll(curveStrokes),
                "every force curve must join to a stroke on strokeCount");
    }

    private static long lines(Path p) throws IOException {
        try (Stream<String> s = Files.lines(p)) {
            return s.filter(l -> !l.isBlank()).count();
        }
    }

    private static Set<Integer> strokeCounts(Path ndjson) throws IOException {
        Set<Integer> out = new HashSet<>();
        try (Stream<String> s = Files.lines(ndjson)) {
            for (String line : (Iterable<String>) s::iterator) {
                if (line.isBlank()) {
                    continue;
                }
                out.add(MAPPER.readTree(line).get("strokeCount").asInt());
            }
        }
        return out;
    }
}
