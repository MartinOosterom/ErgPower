package work.zing.ergpower.pm5.event;

import java.time.Instant;

/**
 * A decoded event from a PM5 data characteristic.
 *
 * <p>The event contract — not the transport — is what subscribers depend on, so the same events are
 * produced whether the underlying {@code Pm5Source} is BLE, simulated, or replay (design decision
 * D3). Every event carries both clocks that make streams recombinable at a moment in time:
 * <ul>
 *   <li>{@link #pmTime()} — the PM5's own elapsed-workout clock (seconds, 0.01&nbsp;s resolution),
 *       the primary axis for aligning streams;</li>
 *   <li>{@link #hostTime()} — wall-clock time the frame was received on the host.</li>
 * </ul>
 *
 * <p>This is a lean first slice: the characteristics validated against real hardware
 * ({@code 0x31}, {@code 0x35}, {@code 0x36}, {@code 0x3D}) are decoded into typed events; every
 * other characteristic is preserved losslessly as a {@link RawFrame} pending the current interface
 * definition (see {@code design.md} → "Empirically validated against real hardware").
 */
public sealed interface Pm5Event
        permits GeneralStatus, AdditionalStatus1, AdditionalStatus2,
                StrokeData, AdditionalStrokeData, SplitData, AdditionalSplitData,
                ForceCurve, RawFrame {

    /** PM5 elapsed-workout time in seconds (0.01 s resolution). {@code NaN} if not carried. */
    double pmTime();

    /** Host wall-clock time the underlying frame was received. */
    Instant hostTime();
}
