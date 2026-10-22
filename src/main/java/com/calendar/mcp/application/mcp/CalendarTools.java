package com.calendar.mcp.application.mcp;

import com.calendar.mcp.application.dtos.EventSummaryResponse;
import com.calendar.mcp.application.dtos.FriendResponse;
import com.calendar.mcp.application.dtos.InvitationResponse;
import com.calendar.mcp.application.mappers.EventSummaryToolMapper;
import com.calendar.mcp.application.mappers.FriendToolMapper;
import com.calendar.mcp.domain.models.NewEvent;
import com.calendar.mcp.domain.services.EventCreationService;
import com.calendar.mcp.domain.services.EventInvitationService;
import com.calendar.mcp.domain.services.FriendsService;
import com.calendar.mcp.domain.services.MyEventsService;
import com.calendar.mcp.exception.BusinessErrorCode;
import com.calendar.mcp.exception.BusinessException;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * The MCP face of the calendar: an inbound adapter, exactly like a @RestController. It parses what
 * the model sent, calls the domain, and formats the answer. No business decision is taken here.
 *
 * <p>The methods return a {@code Mono}, which the async MCP server subscribes itself. That keeps the
 * service reactive end to end — and, because the subscription stays inside the one the HTTP request
 * made, the caller's token is still in the Reactor context when the gateway call needs it.
 */
public class CalendarTools {

    private final MyEventsService myEventsService;
    private final EventCreationService eventCreationService;
    private final FriendsService friendsService;
    private final EventInvitationService eventInvitationService;
    private final EventSummaryToolMapper mapper;
    private final FriendToolMapper friendMapper;

    public CalendarTools(MyEventsService myEventsService, EventCreationService eventCreationService,
                         FriendsService friendsService, EventInvitationService eventInvitationService,
                         EventSummaryToolMapper mapper, FriendToolMapper friendMapper) {
        this.myEventsService = myEventsService;
        this.eventCreationService = eventCreationService;
        this.friendsService = friendsService;
        this.eventInvitationService = eventInvitationService;
        this.mapper = mapper;
        this.friendMapper = friendMapper;
    }

    /**
     * Note what this signature does NOT take: no user id. The caller is whoever the request's token
     * says, so a model cannot ask for someone else's calendar — it has no parameter to do it with.
     */
    @McpTool(name = "listMyEvents",
             description = "List the authenticated user's calendar events overlapping a time range, soonest first, "
                         + "each with the friends going to it. Use it to answer what someone has planned on a day, "
                         + "a week-end or any period, and who they are seeing.",
             annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                                                   idempotentHint = true, openWorldHint = true))
    public Mono<List<EventSummaryResponse>> listMyEvents(
            @McpToolParam(description = "Start of the range, an ISO-8601 instant in UTC, e.g. 2026-10-21T00:00:00Z")
            String from,
            @McpToolParam(description = "End of the range, exclusive, same format. Must be after the start.")
            String to) {

        return parse(from)
                .zipWith(parse(to))
                .flatMapMany(range -> myEventsService.myEvents(range.getT1(), range.getT2()))
                .map(mapper::toResponse)
                .collectList();
    }

    /**
     * Creating is not read-only and not idempotent: calling it twice makes two events. The hints say
     * so, which is what lets a client ask its user before it runs.
     */
    @McpTool(name = "createEvent",
             description = "Create an event on the authenticated user's calendar. It is visible to their friends. "
                         + "Calling this twice creates two events, so confirm the details before calling it.",
             annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false,
                                                   idempotentHint = false, openWorldHint = true))
    public Mono<EventSummaryResponse> createEvent(
            @McpToolParam(description = "What the event is called, as the user would write it")
            String title,
            @McpToolParam(description = "When it starts, an ISO-8601 instant in UTC, e.g. 2026-10-24T18:30:00Z. "
                                      + "For an all-day event any instant on the first day will do.")
            String from,
            @McpToolParam(description = "When it ends, same format. Optional for a timed event; for an all-day one, "
                                      + "any instant on the last day.", required = false)
            String to,
            @McpToolParam(description = "Where it takes place, as the user would say it. Ask them rather than guessing.")
            String location,
            @McpToolParam(description = "True for a whole-day event, false for one with a start and end time")
            Boolean allDay,
            @McpToolParam(description = "A longer description, if the user gave one", required = false)
            String description) {

        return parse(from)
                .flatMap(start -> parseOptional(to)
                        .map(end -> new NewEvent(title, start, end, location, description, Boolean.TRUE.equals(allDay)))
                        .defaultIfEmpty(new NewEvent(title, start, null, location, description,
                                Boolean.TRUE.equals(allDay))))
                .flatMap(eventCreationService::create)
                .map(mapper::toResponse);
    }

    /** No parameters at all: the caller is the token, and there is nothing to narrow. */
    @McpTool(name = "listMyFriends",
             description = "List the authenticated user's friends, closest first, with the handle to use when "
                         + "inviting them to an event. Accepted friendships only, not pending requests.",
             annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                                                   idempotentHint = true, openWorldHint = true))
    public Mono<List<FriendResponse>> listMyFriends() {
        return friendsService.friends()
                .map(friendMapper::toResponse)
                .collectList();
    }

    /**
     * The guest list of one event, which is not always the caller's own friends: a surprise is
     * attended by the honoree's circle. Reading it costs the honoree a disclosure, so it is asked
     * for an event the caller organizes and nothing else.
     */
    @McpTool(name = "listInvitableFriends",
             description = "List who may be invited to an event the authenticated user organizes, with the handle to "
                         + "invite them by. On a surprise party these are the honoree's friends, not the user's own, "
                         + "so call this rather than listMyFriends before inviting anyone to a surprise.",
             annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                                                   idempotentHint = true, openWorldHint = true))
    public Mono<List<FriendResponse>> listInvitableFriends(
            @McpToolParam(description = "The event's id, as createEvent or listMyEvents answered it")
            String eventId) {

        return eventInvitationService.invitableTo(eventId)
                .map(friendMapper::toResponse)
                .collectList();
    }

    /**
     * Takes handles rather than ids, and resolves them against the people this event may be invited
     * to: a model cannot invite someone the calendar would refuse, because it has no way to name them.
     */
    @McpTool(name = "inviteToEvent",
             description = "Invite friends to an event the authenticated user organizes. Give the event id from "
                         + "createEvent or listMyEvents, and the handles from listInvitableFriends. Answers one "
                         + "line per friend, so a refusal does not hide the invitations that went out.",
             annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false,
                                                   idempotentHint = true, openWorldHint = true))
    public Mono<List<InvitationResponse>> inviteToEvent(
            @McpToolParam(description = "The event's id, as createEvent or listMyEvents answered it")
            String eventId,
            @McpToolParam(description = "The friends to invite, by the handle listInvitableFriends gave, e.g. camille#1234")
            List<String> friends) {

        return eventInvitationService.invite(eventId, friends)
                .map(invitations -> invitations.stream().map(friendMapper::toResponse).toList());
    }

    /** A model writes dates as text, and gets them wrong: say so in a way it can act on. */
    private Mono<Instant> parse(String value) {
        if (value == null) {
            return Mono.error(new BusinessException(BusinessErrorCode.TIME_RANGE_UNREADABLE));
        }
        return Mono.fromCallable(() -> Instant.parse(value))
                .onErrorMap(DateTimeParseException.class,
                        cause -> new BusinessException(BusinessErrorCode.TIME_RANGE_UNREADABLE));
    }

    /** An absent date is not a mistake; an unreadable one is. */
    private Mono<Instant> parseOptional(String value) {
        return value == null || value.isBlank() ? Mono.empty() : parse(value);
    }
}
