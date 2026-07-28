package work.zing.ergpower.pm5.api;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.function.Supplier;

import jakarta.annotation.PostConstruct;

import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import work.zing.ergpower.api.model.ConnectionState;
import work.zing.ergpower.api.model.ConnectionStatus;
import work.zing.ergpower.api.model.ForceCurve;
import work.zing.ergpower.api.model.Heartbeat;
import work.zing.ergpower.api.model.LiveMetrics;
import work.zing.ergpower.api.model.LiveSnapshot;
import work.zing.ergpower.api.model.StrokeSummary;
import work.zing.ergpower.api.model.WorkoutDurationType;
import work.zing.ergpower.api.model.WorkoutPhase;
import work.zing.ergpower.api.model.WorkoutState;
import work.zing.ergpower.pm5.event.AdditionalStatus1;
import work.zing.ergpower.pm5.event.AdditionalStatus2;
import work.zing.ergpower.pm5.event.AdditionalStrokeData;
import work.zing.ergpower.pm5.event.GeneralStatus;
import work.zing.ergpower.pm5.event.Pm5Event;
import work.zing.ergpower.pm5.event.StrokeData;

/**
 * Aggregates the live PM5 event stream into a coherent current state (for {@code /live/snapshot} and
 * {@code /connection}) and broadcasts named SSE events (for {@code /live/stream}). Source-agnostic: the
 * {@code SourceManager} feeds it events via {@link #onEvent} and supplies the connection status via
 * {@link #setConnectionProvider}, so it serves live or replayed sources identically.
 */
@Component
public class LiveState {

    private final Sinks.Many<ServerSentEvent<Object>> sse = Sinks.many().multicast().onBackpressureBuffer();

    private volatile Supplier<ConnectionStatus> connectionProvider =
            () -> new ConnectionStatus().state(ConnectionState.DISCONNECTED).since(OffsetDateTime.now());

    private volatile WorkoutState workout;
    private volatile StrokeSummary lastStroke;
    private volatile ForceCurve lastForceCurve;
    private volatile StrokeData lastStrokeData;
    private volatile double elapsedTimeS, distanceM, dragFactor, paceS, avgPaceS;
    private volatile int powerW, strokeRate, totalCalories;
    private volatile Integer heartRateBpm, splitAvgPowerW, projectedDistanceM;
    private volatile Double timeLeftS, distanceLeftM, projectedTimeS, splitAvgPaceS;
    private volatile boolean hasMetrics;

    @PostConstruct
    void startHeartbeat() {
        Flux.interval(Duration.ofSeconds(5)).subscribe(i -> {
            emit("connection", connectionStatus());
            emit("heartbeat", new Heartbeat().t(OffsetDateTime.now()));
        });
    }

    /** Set how connection status is built (the active source provides it); pushes a connection event. */
    public void setConnectionProvider(Supplier<ConnectionStatus> provider) {
        this.connectionProvider = provider;
        emit("connection", connectionStatus());
    }

    /** Clear rolling state — called when the active source changes. */
    public void reset() {
        workout = null;
        lastStroke = null;
        lastForceCurve = null;
        lastStrokeData = null;
        elapsedTimeS = distanceM = dragFactor = paceS = avgPaceS = 0;
        powerW = strokeRate = totalCalories = 0;
        heartRateBpm = splitAvgPowerW = projectedDistanceM = null;
        timeLeftS = distanceLeftM = projectedTimeS = splitAvgPaceS = null;
        hasMetrics = false;
    }

    /** Process one event: update the rolling snapshot and broadcast the relevant SSE events. */
    public void onEvent(Pm5Event e) {
        switch (e) {
            case GeneralStatus g -> {
                elapsedTimeS = g.pmTime();
                distanceM = g.distanceMeters();
                dragFactor = g.dragFactor();
                timeLeftS = g.targetTimeSeconds() == null ? null : g.targetTimeSeconds() - g.pmTime();
                distanceLeftM = g.targetDistanceMeters() == null ? null : g.targetDistanceMeters() - g.distanceMeters();
                workout = toWorkout(g);
                hasMetrics = true;
                emit("workout", workout);
                emit("metrics", metrics());
            }
            case AdditionalStatus1 a -> {
                paceS = a.currentPaceSeconds();
                avgPaceS = a.avgPaceSeconds();
                strokeRate = a.strokeRate();
                heartRateBpm = a.heartRateBpm();
                hasMetrics = true;
                emit("metrics", metrics());
            }
            case AdditionalStatus2 a -> {
                totalCalories = a.totalCalories();
                splitAvgPowerW = a.splitAvgPowerWatts();
                splitAvgPaceS = a.splitAvgPaceSeconds();
            }
            case StrokeData s -> lastStrokeData = s;
            case AdditionalStrokeData a -> {
                powerW = a.strokePowerWatts();
                projectedTimeS = a.projectedWorkTimeSeconds() > 0 ? a.projectedWorkTimeSeconds() : null;
                projectedDistanceM = a.projectedWorkDistanceMeters() > 0 ? a.projectedWorkDistanceMeters() : null;
                lastStroke = toStrokeSummary(a);
                emit("stroke", lastStroke);
                emit("metrics", metrics());
            }
            case work.zing.ergpower.pm5.event.ForceCurve f -> {
                lastForceCurve = toForceCurve(f);
                emit("forceCurve", lastForceCurve);
            }
            default -> { /* splits/raw not surfaced in v1 live */ }
        }
    }

    public ConnectionStatus connectionStatus() {
        return connectionProvider.get();
    }

    public LiveSnapshot snapshot() {
        return new LiveSnapshot()
                .connection(connectionStatus())
                .workout(workout)
                .metrics(hasMetrics ? metrics() : null)
                .lastStroke(lastStroke)
                .lastForceCurve(lastForceCurve);
    }

    public Flux<ServerSentEvent<Object>> liveEvents() {
        return sse.asFlux();
    }

    private LiveMetrics metrics() {
        return new LiveMetrics()
                .elapsedTimeS(bd(elapsedTimeS)).distanceM(bd(distanceM))
                .powerW(powerW).paceSecondsPer500(bd(paceS)).avgPaceSecondsPer500(bd(avgPaceS))
                .strokeRate(strokeRate).heartRateBpm(heartRateBpm).dragFactor((int) dragFactor)
                .timeLeftS(bd(timeLeftS)).distanceLeftM(bd(distanceLeftM))
                .projectedTimeS(bd(projectedTimeS)).projectedDistanceM(projectedDistanceM)
                .totalCalories(totalCalories).splitAvgPowerW(splitAvgPowerW)
                .splitAvgPaceSecondsPer500(bd(splitAvgPaceS));
    }

    private WorkoutState toWorkout(GeneralStatus g) {
        WorkoutDurationType dt = switch (g.workoutDurationType()) {
            case GeneralStatus.DURATION_TYPE_TIME -> WorkoutDurationType.TIME;
            case GeneralStatus.DURATION_TYPE_DISTANCE -> WorkoutDurationType.DISTANCE;
            default -> WorkoutDurationType.OTHER;
        };
        return new WorkoutState()
                .type(g.workoutType()).durationType(dt)
                .targetTimeS(bd(g.targetTimeSeconds())).targetDistanceM(g.targetDistanceMeters())
                .phase(mapPhase(g.workoutState())).intervalCount(0);
    }

    private StrokeSummary toStrokeSummary(AdditionalStrokeData a) {
        StrokeData s = lastStrokeData;
        StrokeSummary ss = new StrokeSummary().index(a.strokeCount()).pmTime(bd(a.pmTime())).powerW(a.strokePowerWatts());
        if (s != null && s.strokeCount() == a.strokeCount()) {
            ss.drivePeakForceN(bd(s.peakDriveForceNewtons())).avgDriveForceN(bd(s.avgDriveForceNewtons()))
              .driveTimeS(bd(s.driveTimeSeconds())).strokeDistanceM(bd(s.strokeDistanceMeters()));
        }
        return ss;
    }

    private ForceCurve toForceCurve(work.zing.ergpower.pm5.event.ForceCurve f) {
        ForceCurve fc = new ForceCurve().strokeIndex(f.strokeCount()).pmTime(bd(f.pmTime())).peakN(bd(f.peak()));
        for (double v : f.forcesNewtons()) {
            fc.addForcesNItem(bd(v));
        }
        return fc;
    }

    private static WorkoutPhase mapPhase(int ws) {
        return switch (ws) {
            case 0, 2 -> WorkoutPhase.WAITING;
            case 1, 4, 5, 6, 7 -> WorkoutPhase.ROWING;
            case 3, 8, 9 -> WorkoutPhase.RESTING;
            default -> WorkoutPhase.ENDED;
        };
    }

    private void emit(String event, Object data) {
        if (data != null) {
            sse.tryEmitNext(ServerSentEvent.builder(data).event(event).build());
        }
    }

    private static BigDecimal bd(Double v) {
        return v == null ? null : BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }
}
