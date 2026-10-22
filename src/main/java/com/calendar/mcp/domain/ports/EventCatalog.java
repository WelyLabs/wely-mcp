package com.calendar.mcp.domain.ports;

import com.calendar.mcp.domain.models.Attendance;
import com.calendar.mcp.domain.models.EventSummary;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * The calendar as this service reads it, on behalf of whoever the current call carries the token of.
 * Reading only: what changes the calendar lives in {@link EventWriter}, so a service that merely
 * answers questions cannot be handed the means to write.
 */
public interface EventCatalog {

    /**
     * The caller's events overlapping [from, to). The range is a request, not a guarantee: the
     * domain filters again, so an API that ignores it cannot widen the answer unnoticed.
     */
    Flux<EventSummary> subscribedEvents(Instant from, Instant to);

    /** Where the caller stands on that event, and who is waiting on it. */
    Mono<Attendance> attendanceOf(String eventId);
}
