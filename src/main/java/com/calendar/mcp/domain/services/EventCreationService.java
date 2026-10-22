package com.calendar.mcp.domain.services;

import com.calendar.mcp.domain.models.EventSummary;
import com.calendar.mcp.domain.models.NewEvent;
import com.calendar.mcp.domain.ports.EventCatalog;
import com.calendar.mcp.exception.BusinessErrorCode;
import com.calendar.mcp.exception.BusinessException;
import reactor.core.publisher.Mono;

/** Creates an event, refusing here what the calendar would refuse anyway -- but in words a model can act on. */
public class EventCreationService {

    private final EventCatalog eventCatalog;

    public EventCreationService(EventCatalog eventCatalog) {
        this.eventCatalog = eventCatalog;
    }

    /**
     * The checks are the calendar's own: a title, a place, and an end after the start. They are
     * repeated here so the answer names the missing field instead of returning a validation body,
     * and so a malformed call never reaches the API.
     */
    public Mono<EventSummary> create(NewEvent event) {
        if (event == null || isBlank(event.title())) {
            return Mono.error(new BusinessException(BusinessErrorCode.TITLE_REQUIRED));
        }
        if (isBlank(event.location())) {
            return Mono.error(new BusinessException(BusinessErrorCode.LOCATION_REQUIRED));
        }
        if (event.startDate() == null) {
            return Mono.error(new BusinessException(BusinessErrorCode.TIME_RANGE_UNREADABLE));
        }
        if (event.endDate() != null && event.endDate().isBefore(event.startDate())) {
            return Mono.error(new BusinessException(BusinessErrorCode.END_BEFORE_START));
        }
        return eventCatalog.create(event.normalised());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
