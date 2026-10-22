package com.calendar.mcp.infrastructure.http.dtos;

import java.time.Instant;

/**
 * The body wely-events expects to create an event. Only the fields this service sets: visibility is
 * left out on purpose, so the calendar applies its own default (FRIENDS) rather than a model's guess.
 */
public record GatewayEventRequest(
        String title,
        Instant startDate,
        Instant endDate,
        String location,
        String description,
        boolean allDay) {
}
