package com.calendar.mcp.domain.ports;

import com.calendar.mcp.domain.models.EventSummary;
import reactor.core.publisher.Flux;

import java.time.Instant;

/** The calendar as this service reads it, on behalf of whoever the current call carries the token of. */
public interface EventCatalog {

    /**
     * The caller's events overlapping [from, to). The range is a request, not a guarantee: the
     * domain filters again, so an API that ignores it cannot widen the answer unnoticed.
     */
    Flux<EventSummary> subscribedEvents(Instant from, Instant to);
}
