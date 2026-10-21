package com.calendar.mcp.exception;

/** Business failures of this service. Codes follow the MCP-BUS-nnn form the other services use. */
public enum BusinessErrorCode {

    TIME_RANGE_INVALID("MCP-BUS-001", "The end of the range must come after its start."),
    TIME_RANGE_UNREADABLE("MCP-BUS-002", "Dates must be ISO-8601 instants, for example 2026-10-21T00:00:00Z.");

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
