package com.calendar.mcp.application.dtos;

/**
 * What the tool hands back to the model. Dates are ISO-8601 strings on purpose: a model reads and
 * compares them without help, where an epoch number would need explaining.
 */
public record EventSummaryResponse(
        String id,
        String title,
        String startDate,
        String endDate,
        String location,
        boolean allDay) {
}
