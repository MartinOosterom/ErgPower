package work.zing.ergpower.pm5.source;

import reactor.core.publisher.Flux;

import work.zing.ergpower.pm5.event.Pm5Event;

/**
 * A source of decoded PM5 events — the transport-agnostic seam (design decision D3). Subscribers
 * (storage now; live display later) depend only on this, so the real BLE source, a simulated source,
 * and a replay source are interchangeable without any change downstream.
 */
public interface Pm5Source {

    /** The stream of decoded events. Cold sources (e.g. replay) start on subscribe. */
    Flux<Pm5Event> events();
}
