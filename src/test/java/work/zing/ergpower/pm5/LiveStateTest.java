package work.zing.ergpower.pm5;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;

import reactor.core.Disposable;

import work.zing.ergpower.api.model.LiveSnapshot;
import work.zing.ergpower.pm5.api.LiveState;
import work.zing.ergpower.pm5.source.ReplayPm5Source;

/**
 * Feeds the real captured row through {@link LiveState} and checks it aggregates into a coherent
 * snapshot and broadcasts the expected named SSE events — no PM5, no HTTP.
 */
class LiveStateTest {

    private static final Path FIXTURE =
            Path.of("src/test/resources/captures/2026-07-28_rowerg_432234859_289m_28strokes.ndjson");

    @Test
    void aggregatesFixtureIntoSnapshotAndSse() {
        assumeTrue(Files.exists(FIXTURE), "fixture missing");
        LiveState live = new LiveState();

        CopyOnWriteArrayList<ServerSentEvent<Object>> sse = new CopyOnWriteArrayList<>();
        Disposable sub = live.liveEvents().subscribe(sse::add);
        new ReplayPm5Source(FIXTURE).events().toIterable().forEach(live::onEvent);
        sub.dispose();

        long metrics = sse.stream().filter(e -> "metrics".equals(e.event())).count();
        long strokes = sse.stream().filter(e -> "stroke".equals(e.event())).count();
        long curves = sse.stream().filter(e -> "forceCurve".equals(e.event())).count();
        System.out.printf("sse: metrics=%d stroke=%d forceCurve=%d%n", metrics, strokes, curves);

        assertEquals(26, curves, "one forceCurve event per reassembled curve");
        assertEquals(28, strokes, "one stroke event per stroke");
        assertTrue(metrics > 100, "metrics streamed at the status cadence, got " + metrics);

        LiveSnapshot snap = live.snapshot();
        assertNotNull(snap.getMetrics());
        assertEquals(289.7, snap.getMetrics().getDistanceM().doubleValue(), 0.2);
        assertNotNull(snap.getLastForceCurve());
        assertFalse(snap.getLastForceCurve().getForcesN().isEmpty());
    }
}
