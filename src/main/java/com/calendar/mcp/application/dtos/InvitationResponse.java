package com.calendar.mcp.application.dtos;

/** What became of one invitation, so a partial success is reported as exactly that. */
public record InvitationResponse(String friend, boolean invited, String reason) {
}
