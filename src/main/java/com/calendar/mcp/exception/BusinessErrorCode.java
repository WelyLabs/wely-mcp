package com.calendar.mcp.exception;

/** Business failures of this service. Codes follow the MCP-BUS-nnn form the other services use. */
public enum BusinessErrorCode {

    TIME_RANGE_INVALID("MCP-BUS-001", "The end of the range must come after its start."),
    TIME_RANGE_UNREADABLE("MCP-BUS-002", "Dates must be ISO-8601 instants, for example 2026-10-21T00:00:00Z."),
    TITLE_REQUIRED("MCP-BUS-003", "An event needs a title."),
    LOCATION_REQUIRED("MCP-BUS-004", "An event needs a place. Ask where it is rather than inventing one."),
    END_BEFORE_START("MCP-BUS-005", "An event cannot end before it starts."),
    EVENT_ID_REQUIRED("MCP-BUS-006", "Say which event to invite to: the id createEvent or listMyEvents answered."),
    NOBODY_TO_INVITE("MCP-BUS-007", "Name at least one friend to invite, by their handle from listMyFriends.");

    private final String code;
    private final String message;

    BusinessErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }
}
