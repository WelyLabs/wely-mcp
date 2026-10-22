package com.calendar.mcp.infrastructure.http.dtos;

/** The slice of wely-social's FriendSummary this service reads. */
public record GatewayFriendSummaryResponse(
        String userId,
        String userName,
        Integer hashtag,
        String closeness) {
}
