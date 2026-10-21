package com.calendar.mcp.domain.models;

import java.time.Instant;

/**
 * What an agent needs to talk about an event. Deliberately narrower than the API's own payload:
 * every field costs the model context, and covers, signed picture URLs or poll details help it none.
 */
public record EventSummary(
        String id,
        String title,
        Instant startDate,
        Instant endDate,
        String location,
        boolean allDay) {

    /** An event with no end is treated as ending when it starts, so a range still matches it. */
    public Instant effectiveEnd() {
        return endDate != null ? endDate : startDate;
    }
}
