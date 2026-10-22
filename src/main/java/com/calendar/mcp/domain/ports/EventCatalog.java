package com.calendar.mcp.domain.ports;

import com.calendar.mcp.domain.models.EventSummary;
import com.calendar.mcp.domain.models.GuestPolicy;
import com.calendar.mcp.domain.models.NewEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

/** The calendar as this service reads it, on behalf of whoever the current call carries the token of. */
public interface EventCatalog {

    /**
     * The caller's events overlapping [from, to). The range is a request, not a guarantee: the
     * domain filters again, so an API that ignores it cannot widen the answer unnoticed.
     */
    Flux<EventSummary> subscribedEvents(Instant from, Instant to);

    /** Creates the event on the caller's calendar and answers it as stored. */
    Mono<EventSummary> create(NewEvent event);

    /** Invites one friend to an event the caller organizes. */
    Mono<Void> invite(String eventId, String userId);

    /** What decides who may be invited to that event: its kind, and the honoree on a surprise. */
    Mono<GuestPolicy> guestPolicyOf(String eventId);
}
