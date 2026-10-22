package com.calendar.mcp.domain.services;

import com.calendar.mcp.domain.models.Friend;
import com.calendar.mcp.domain.models.Invitation;
import com.calendar.mcp.domain.ports.EventCatalog;
import com.calendar.mcp.exception.BusinessErrorCode;
import com.calendar.mcp.exception.BusinessException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/** Invites friends to an event, one answer per friend. */
public class EventInvitationService {

    /** Invitations go out one at a time: the calendar takes one invitee per call, and order is the model's. */
    static final int INVITE_CONCURRENCY = 1;

    private final EventCatalog eventCatalog;
    private final FriendsService friendsService;

    public EventInvitationService(EventCatalog eventCatalog, FriendsService friendsService) {
        this.eventCatalog = eventCatalog;
        this.friendsService = friendsService;
    }

    /**
     * Each handle is resolved against the caller's own friends before anything is sent -- which is
     * what keeps a model from inviting someone they are not friends with -- and every outcome is
     * reported, so one refusal does not hide the invitations that did go out.
     */
    public Mono<List<Invitation>> invite(String eventId, List<String> handles) {
        if (eventId == null || eventId.isBlank()) {
            return Mono.error(new BusinessException(BusinessErrorCode.EVENT_ID_REQUIRED));
        }
        if (handles == null || handles.isEmpty()) {
            return Mono.error(new BusinessException(BusinessErrorCode.NOBODY_TO_INVITE));
        }
        return Flux.fromIterable(handles)
                .concatMap(handle -> inviteOne(eventId, handle), INVITE_CONCURRENCY)
                .collectList();
    }

    private Mono<Invitation> inviteOne(String eventId, String handle) {
        return friendsService.byHandle(handle)
                .flatMap(friend -> sendTo(eventId, friend))
                .defaultIfEmpty(Invitation.refused(handle, "not among your friends"))
                .onErrorResume(cause -> Mono.just(Invitation.refused(handle, "the calendar refused the invitation")));
    }

    private Mono<Invitation> sendTo(String eventId, Friend friend) {
        return eventCatalog.invite(eventId, friend.userId())
                .thenReturn(Invitation.sent(friend.handle()));
    }
}
