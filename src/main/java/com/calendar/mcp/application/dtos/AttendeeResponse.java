package com.calendar.mcp.application.dtos;

/**
 * One friend going. No user id: it would mean nothing to a model and names are what gets spoken.
 * FRIEND, CLOSE or INTIMATE says how close they are, which is why the list is ordered as it is.
 */
public record AttendeeResponse(String name, String closeness) {
}
