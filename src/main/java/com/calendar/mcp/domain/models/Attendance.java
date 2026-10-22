package com.calendar.mcp.domain.models;

import java.util.List;

/**
 * Where the caller stands on one event. The calendar answers the full participant list only to
 * someone involved, so a non-empty list is itself the answer to "am I in?" -- no user id needed.
 */
public record Attendance(boolean takingPart, List<String> requestedIds) {

    public Attendance {
        requestedIds = List.copyOf(requestedIds == null ? List.of() : requestedIds);
    }

    /** For anyone but the organizer, a request in that list can only be their own. */
    public boolean hasAsked() {
        return !requestedIds.isEmpty();
    }
}
