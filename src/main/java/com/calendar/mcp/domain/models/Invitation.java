package com.calendar.mcp.domain.models;

/** What became of one invitation. A refusal is an outcome, not an exception: the others still went out. */
public record Invitation(String handle, boolean sent, String reason) {

    public static Invitation sent(String handle) {
        return new Invitation(handle, true, null);
    }

    public static Invitation refused(String handle, String reason) {
        return new Invitation(handle, false, reason);
    }
}
