package com.calendar.mcp.domain.services;

import com.calendar.mcp.domain.models.EventSummary;
import com.calendar.mcp.domain.ports.EventCatalog;
import com.calendar.mcp.exception.BusinessErrorCode;
import com.calendar.mcp.exception.BusinessException;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.Comparator;

/** Answers "what do I have between these two dates", soonest first. */
public class MyEventsService {

    /** A tool answer is read by a model: past a few dozen events it stops helping and only costs context. */
    static final int MAX_EVENTS = 50;

    private final EventCatalog eventCatalog;

    public MyEventsService(EventCatalog eventCatalog) {
        this.eventCatalog = eventCatalog;
    }

    /**
     * The caller's events overlapping [from, to), soonest first and capped at {@value #MAX_EVENTS}.
     * An event counts as overlapping as soon as it is not wholly before or wholly after the range.
     *
     * <p>The range goes to the catalog *and* is applied here. That is deliberate rather than
     * redundant: the rule belongs to the domain, and an API version that ignored the parameters
     * would otherwise widen this answer silently.
     */
    public Flux<EventSummary> myEvents(Instant from, Instant to) {
        if (from == null || to == null || !to.isAfter(from)) {
            return Flux.error(new BusinessException(BusinessErrorCode.TIME_RANGE_INVALID));
        }
        return eventCatalog.subscribedEvents(from, to)
                .filter(event -> overlaps(event, from, to))
                .sort(Comparator.comparing(EventSummary::startDate))
                .take(MAX_EVENTS);
    }

    private boolean overlaps(EventSummary event, Instant from, Instant to) {
        if (event.startDate() == null) {
            return false;
        }
        return event.startDate().isBefore(to) && !event.effectiveEnd().isBefore(from);
    }
}
