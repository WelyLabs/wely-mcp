package com.calendar.mcp.domain.models;

/**
 * One of the caller's friends going to an event. The calendar answers with ids only -- names are the
 * client's job -- so the name here is resolved separately, and may be absent when that lookup fails.
 */
public record Attendee(String userId, String name, String closeness) {

    /** What to show when the directory could not name them: never a bare UUID, which means nothing. */
    public static Attendee unnamed(String userId, String closeness) {
        return new Attendee(userId, null, closeness);
    }

    public boolean isNamed() {
        return name != null && !name.isBlank();
    }
}
