package com.calendar.mcp.domain.ports;

import com.calendar.mcp.domain.models.EventSummary;
import com.calendar.mcp.domain.models.NewEvent;
import reactor.core.publisher.Mono;

/**
 * What changes the calendar. Split from {@link EventCatalog} so the read side can be injected on its
 * own: a service that only answers questions has no way to write, and a test double for one does not
 * have to stub the other.
 */
public interface EventWriter {

    /** Creates the event on the caller's calendar and answers it as stored. */
    Mono<EventSummary> create(NewEvent event);

    /** Invites one friend to an event the caller organizes. */
    Mono<Void> invite(String eventId, String userId);

    /** Joins the event, or asks to when the calendar wants the organizer's answer first. */
    Mono<Void> join(String eventId);

    /** The organizer answers someone who asked to join. */
    Mono<Void> answerRequest(String eventId, String userId, boolean accept);
}
