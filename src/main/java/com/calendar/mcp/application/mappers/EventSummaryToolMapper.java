package com.calendar.mcp.application.mappers;

import com.calendar.mcp.application.dtos.AttendeeResponse;
import com.calendar.mcp.application.dtos.EventSummaryResponse;
import com.calendar.mcp.domain.models.Attendee;
import com.calendar.mcp.domain.models.EventSummary;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface EventSummaryToolMapper {

    /** Instant maps to its ISO-8601 form through Instant.toString(). */
    EventSummaryResponse toResponse(EventSummary event);

    default AttendeeResponse toResponse(Attendee attendee) {
        return new AttendeeResponse(attendee.name(), attendee.closeness());
    }

    /**
     * Whoever could not be named is left out of the list but still counted: a UUID tells a model
     * nothing, while friendsGoingCount keeps the answer honest about how many people go.
     */
    default List<AttendeeResponse> toResponses(List<Attendee> attendees) {
        return attendees == null ? List.of()
                : attendees.stream().filter(Attendee::isNamed).map(this::toResponse).toList();
    }
}
