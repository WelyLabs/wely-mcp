package com.calendar.mcp.infrastructure.http.dtos;

/** The slice of wely-users' FriendProfileResponse this service reads: enough to name someone. */
public record GatewayFriendProfileResponse(
        String userName,
        String firstName,
        String lastName) {

    /**
     * The name a person would use: their first and last name when known, else the handle they chose.
     * Null when neither is set, which leaves the attendee unnamed rather than named with a UUID.
     */
    public String displayName() {
        String full = ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
        if (!full.isBlank()) {
            return full;
        }
        return userName == null || userName.isBlank() ? null : userName;
    }
}
