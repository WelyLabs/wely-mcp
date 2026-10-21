package com.calendar.mcp.infrastructure.http.dtos;

/** One of the caller's friends going to an event, as the calendar answers it: an id and a closeness. */
public record GatewayFriendGoingResponse(String userId, String closeness) {
}
