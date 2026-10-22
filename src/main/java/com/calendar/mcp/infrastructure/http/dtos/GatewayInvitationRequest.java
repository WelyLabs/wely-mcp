package com.calendar.mcp.infrastructure.http.dtos;

/** The body wely-events expects to invite someone: the friend's business id. */
public record GatewayInvitationRequest(String userId) {
}
