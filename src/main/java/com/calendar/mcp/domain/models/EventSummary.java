package com.calendar.mcp.domain.models;

import java.time.Instant;
import java.util.List;

/**
 * What an agent needs to talk about an event. Deliberately narrower than the API's own payload:
 * every field costs the model context, and covers, signed picture URLs or poll details help it none.
 */
public record EventSummary(
        String id,
        String title,
        Instant startDate,
        Instant endDate,
        String location,
        boolean allDay,
        /** The caller's friends going, closest first, at most five -- the calendar's own cap. */
        List<Attendee> friendsGoing,
        /** How many friends go in all, uncapped, so "and 7 others" stays sayable. */
        int friendsGoingCount) {

    public EventSummary {
        friendsGoing = friendsGoing == null ? List.of() : List.copyOf(friendsGoing);
    }

    /** An event with no end is treated as ending when it starts, so a range still matches it. */
    public Instant effectiveEnd() {
        return endDate != null ? endDate : startDate;
    }

    public EventSummary withFriendsGoing(List<Attendee> resolved) {
        return new EventSummary(id, title, startDate, endDate, location, allDay, resolved, friendsGoingCount);
    }
}
