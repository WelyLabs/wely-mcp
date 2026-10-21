package com.calendar.mcp.domain.ports;

import com.calendar.mcp.domain.models.EventSummary;
import reactor.core.publisher.Flux;

/** The calendar as this service reads it, on behalf of whoever the current call carries the token of. */
public interface EventCatalog {

    /** Every event the caller is subscribed to, in no particular order. */
    Flux<EventSummary> subscribedEvents();
}
