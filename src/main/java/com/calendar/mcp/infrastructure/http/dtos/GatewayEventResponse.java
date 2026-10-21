package com.calendar.mcp.infrastructure.http.dtos;

import java.time.Instant;

/**
 * The slice of wely-events' EventResponse this service reads. Unknown fields are ignored, so the
 * calendar API may grow without breaking this client.
 */
public record GatewayEventResponse(
        String id,
        String title,
        Instant startDate,
        Instant endDate,
        String location,
        boolean allDay) {
}
