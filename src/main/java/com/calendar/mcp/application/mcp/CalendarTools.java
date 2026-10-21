package com.calendar.mcp.application.mcp;

import com.calendar.mcp.application.dtos.EventSummaryResponse;
import com.calendar.mcp.application.mappers.EventSummaryToolMapper;
import com.calendar.mcp.domain.services.MyEventsService;
import com.calendar.mcp.exception.BusinessErrorCode;
import com.calendar.mcp.exception.BusinessException;
import com.calendar.mcp.exception.TechnicalErrorCode;
import com.calendar.mcp.exception.TechnicalException;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * The MCP face of the calendar: an inbound adapter, exactly like a @RestController. It parses what
 * the model sent, calls the domain, and formats the answer. No business decision is taken here.
 */
public class CalendarTools {

    /** A model is waiting on the other end: failing clearly beats hanging. */
    static final Duration CALL_TIMEOUT = Duration.ofSeconds(10);

    private final MyEventsService myEventsService;
    private final EventSummaryToolMapper mapper;

    public CalendarTools(MyEventsService myEventsService, EventSummaryToolMapper mapper) {
        this.myEventsService = myEventsService;
        this.mapper = mapper;
    }

    /**
     * Note what this signature does NOT take: no user id. The caller is whoever the request's token
     * says, so a model cannot ask for someone else's calendar — it has no parameter to do it with.
     */
    @Tool(name = "listMyEvents",
          description = "List the authenticated user's calendar events overlapping a time range, soonest first. "
                      + "Use it to answer what someone has planned on a day, a week-end or any period.")
    public List<EventSummaryResponse> listMyEvents(
            @ToolParam(description = "Start of the range, an ISO-8601 instant in UTC, e.g. 2026-10-21T00:00:00Z")
            String from,
            @ToolParam(description = "End of the range, exclusive, same format. Must be after the start.")
            String to) {

        Instant start = parse(from);
        Instant end = parse(to);
        Authentication caller = currentCaller();

        // Spring AI runs a @Tool method inside Mono.fromCallable(...).subscribeOn(boundedElastic),
        // so this thread exists to block on; the chain underneath stays reactive. The caller's
        // authentication is put back into the Reactor context because this new subscription
        // starts outside the one the HTTP request made.
        return myEventsService.myEvents(start, end)
                .map(mapper::toResponse)
                .collectList()
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(caller))
                .block(CALL_TIMEOUT);
    }

    private Instant parse(String value) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException | NullPointerException cause) {
            throw new BusinessException(BusinessErrorCode.TIME_RANGE_UNREADABLE);
        }
    }

    /** Without a caller there is nothing to answer: the calendar is never read anonymously. */
    private Authentication currentCaller() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new TechnicalException(TechnicalErrorCode.CALLER_TOKEN_MISSING);
        }
        return authentication;
    }
}
