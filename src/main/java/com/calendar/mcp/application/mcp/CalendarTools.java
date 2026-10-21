package com.calendar.mcp.application.mcp;

import com.calendar.mcp.application.dtos.EventSummaryResponse;
import com.calendar.mcp.application.mappers.EventSummaryToolMapper;
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
 * <p>The method returns a {@code Mono}, which the async MCP server subscribes itself. That keeps the
 * service reactive end to end — and, because the subscription stays inside the one the HTTP request
 * made, the caller's token is still in the Reactor context when the gateway call needs it.
 */
public class CalendarTools {

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
    @McpTool(name = "listMyEvents",
             description = "List the authenticated user's calendar events overlapping a time range, soonest first. "
                         + "Use it to answer what someone has planned on a day, a week-end or any period.")
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

    /** A model writes dates as text, and gets them wrong: say so in a way it can act on. */
    private Mono<Instant> parse(String value) {
        if (value == null) {
            return Mono.error(new BusinessException(BusinessErrorCode.TIME_RANGE_UNREADABLE));
        }
        return Mono.fromCallable(() -> Instant.parse(value))
                .onErrorMap(DateTimeParseException.class,
                        cause -> new BusinessException(BusinessErrorCode.TIME_RANGE_UNREADABLE));
    }
}
