package com.calendar.mcp.domain.services;

import com.calendar.mcp.domain.models.Friend;
import com.calendar.mcp.domain.models.Invitation;
import com.calendar.mcp.domain.ports.EventCatalog;
import com.calendar.mcp.domain.ports.SocialGraph;
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
    private final SocialGraph socialGraph;

    public EventInvitationService(EventCatalog eventCatalog, FriendsService friendsService, SocialGraph socialGraph) {
        this.eventCatalog = eventCatalog;
        this.friendsService = friendsService;
        this.socialGraph = socialGraph;
    }

    /**
     * Who may be invited to that event: the honoree's friends on a surprise, the caller's own
     * otherwise. The same list the invitations are checked against, so what the tool offers and what
     * the calendar accepts cannot drift apart.
     *
     * <p>A surprise's guests are named by their handle alone: they are the honoree's friends, not
     * necessarily the caller's, and their profile is not the caller's to read.
     */
    public Flux<Friend> invitableTo(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return Flux.error(new BusinessException(BusinessErrorCode.EVENT_ID_REQUIRED));
        }
        return eventCatalog.guestPolicyOf(eventId)
                .flatMapMany(policy -> policy.namesAnHonoree()
                        ? socialGraph.friendsOf(policy.honoreeId())
                        : friendsService.friends());
    }

    /**
     * Each handle is resolved against the people that event may be invited to -- which keeps a model
     * from inviting someone the calendar would refuse -- and every outcome is reported, so one
     * refusal does not hide the invitations that did go out.
     */
    public Mono<List<Invitation>> invite(String eventId, List<String> handles) {
        if (eventId == null || eventId.isBlank()) {
            return Mono.error(new BusinessException(BusinessErrorCode.EVENT_ID_REQUIRED));
        }
        if (handles == null || handles.isEmpty()) {
            return Mono.error(new BusinessException(BusinessErrorCode.NOBODY_TO_INVITE));
        }
        return invitableTo(eventId)
                .collectList()
                .flatMap(invitable -> Flux.fromIterable(handles)
                        .concatMap(handle -> inviteOne(eventId, invitable, handle), INVITE_CONCURRENCY)
                        .collectList());
    }

    private Mono<Invitation> inviteOne(String eventId, List<Friend> invitable, String handle) {
        return Mono.justOrEmpty(matching(invitable, handle))
                .flatMap(friend -> sendTo(eventId, friend))
                .defaultIfEmpty(Invitation.refused(handle, reasonFor(invitable)))
                .onErrorResume(cause -> Mono.just(Invitation.refused(handle, "the calendar refused the invitation")));
    }

    private Friend matching(List<Friend> invitable, String handle) {
        if (handle == null || handle.isBlank()) {
            return null;
        }
        String wanted = handle.trim();
        return invitable.stream()
                .filter(friend -> wanted.equalsIgnoreCase(friend.handle()) || wanted.equalsIgnoreCase(friend.userName()))
                .findFirst()
                .orElse(null);
    }

    /** Says which list the handle was looked for in, which is the useful half of the refusal. */
    private String reasonFor(List<Friend> invitable) {
        return invitable.isEmpty() ? "nobody can be invited to this event"
                : "not among the people who may be invited to this event";
    }

    private Mono<Invitation> sendTo(String eventId, Friend friend) {
        return eventCatalog.invite(eventId, friend.userId())
                .thenReturn(Invitation.sent(friend.handle()));
    }
}
