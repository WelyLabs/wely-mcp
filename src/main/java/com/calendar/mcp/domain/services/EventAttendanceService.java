package com.calendar.mcp.domain.services;

import com.calendar.mcp.domain.models.Attendance;
import com.calendar.mcp.domain.models.Friend;
import com.calendar.mcp.domain.models.Invitation;
import com.calendar.mcp.domain.models.JoinOutcome;
import com.calendar.mcp.domain.ports.EventCatalog;
import com.calendar.mcp.domain.ports.SocialGraph;
import com.calendar.mcp.exception.BusinessErrorCode;
import com.calendar.mcp.exception.BusinessException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Joining an event, and answering those who ask to join one's own. */
public class EventAttendanceService {

    /** A handful of askers at most: enough to name them quickly without hammering the directory. */
    static final int NAME_LOOKUP_CONCURRENCY = 4;

    private final EventCatalog eventCatalog;
    private final SocialGraph socialGraph;

    public EventAttendanceService(EventCatalog eventCatalog, SocialGraph socialGraph) {
        this.eventCatalog = eventCatalog;
        this.socialGraph = socialGraph;
    }

    /**
     * Joins, or asks to on a surprise whose honoree is only a plain friend. Where the caller already
     * stands is read first, because the calendar's own endpoint is a toggle: calling it blindly a
     * second time would quietly take them back out.
     */
    public Mono<JoinOutcome> join(String eventId) {
        return attendance(eventId).flatMap(standing -> {
            if (standing.takingPart()) {
                return Mono.just(JoinOutcome.ALREADY_TAKING_PART);
            }
            if (standing.hasAsked()) {
                return Mono.just(JoinOutcome.ALREADY_REQUESTED);
            }
            return eventCatalog.join(eventId)
                    .then(attendance(eventId))
                    .map(after -> after.takingPart() ? JoinOutcome.JOINED : JoinOutcome.REQUESTED);
        });
    }

    /** Who is waiting on an event the caller organizes, named through the people directory. */
    public Flux<Friend> pendingRequests(String eventId) {
        return attendance(eventId)
                .flatMapMany(standing -> Flux.fromIterable(standing.requestedIds()))
                .flatMap(socialGraph::person, NAME_LOOKUP_CONCURRENCY);
    }

    /**
     * Answers one asker, named by the handle {@code pendingRequests} gave. Resolving it against the
     * standing requests is what keeps a model from answering for somebody who never asked.
     */
    public Mono<Invitation> answer(String eventId, String handle, boolean accept) {
        if (handle == null || handle.isBlank()) {
            return Mono.error(new BusinessException(BusinessErrorCode.NOBODY_TO_INVITE));
        }
        String wanted = handle.trim();
        return pendingRequests(eventId)
                .filter(asker -> wanted.equalsIgnoreCase(asker.handle()) || wanted.equalsIgnoreCase(asker.userName()))
                .next()
                .flatMap(asker -> eventCatalog.answerRequest(eventId, asker.userId(), accept)
                        .thenReturn(Invitation.sent(asker.handle())))
                .defaultIfEmpty(Invitation.refused(wanted, "nobody is waiting on this event with that handle"))
                .onErrorResume(cause -> Mono.just(Invitation.refused(wanted, "the calendar refused the answer")));
    }

    private Mono<Attendance> attendance(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return Mono.error(new BusinessException(BusinessErrorCode.EVENT_ID_REQUIRED));
        }
        return eventCatalog.attendanceOf(eventId);
    }
}
