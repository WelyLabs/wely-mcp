package com.calendar.mcp.application.mappers;

import com.calendar.mcp.application.dtos.EventSummaryResponse;
import com.calendar.mcp.domain.models.EventSummary;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface EventSummaryToolMapper {

    /** Instant maps to its ISO-8601 form through Instant.toString(). */
    EventSummaryResponse toResponse(EventSummary event);
}
