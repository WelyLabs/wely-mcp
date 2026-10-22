package com.calendar.mcp.application.dtos;

/**
 * One friend, as the model sees them. The handle is the identifier to pass back to inviteToEvent --
 * no user id is exposed, so a model can only act on people it was actually told about.
 */
public record FriendResponse(String handle, String name, String closeness) {
}
