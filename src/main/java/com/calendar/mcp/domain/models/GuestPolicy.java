package com.calendar.mcp.domain.models;

/**
 * What decides who may be invited to an event: on a SURPRISE the honoree's friends, on anything else
 * the organizer's own. It mirrors the rule wely-events enforces, so the tool offers exactly the
 * people the calendar would accept rather than letting a model discover the rule by being refused.
 */
public record GuestPolicy(boolean surprise, String honoreeId) {

    public static GuestPolicy organizersFriends() {
        return new GuestPolicy(false, null);
    }

    public static GuestPolicy honoreesFriends(String honoreeId) {
        return new GuestPolicy(true, honoreeId);
    }

    /** A surprise with no honoree cannot name a circle: treated as the organizer's own friends. */
    public boolean namesAnHonoree() {
        return surprise && honoreeId != null && !honoreeId.isBlank();
    }
}
