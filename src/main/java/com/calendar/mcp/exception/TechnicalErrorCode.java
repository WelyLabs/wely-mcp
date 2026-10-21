package com.calendar.mcp.exception;

/** Technical failures of this service. Codes follow the MCP-TEC-nnn form the other services use. */
public enum TechnicalErrorCode {

    GATEWAY_UNREACHABLE("MCP-TEC-001", "The calendar API could not be reached."),
    GATEWAY_REFUSED("MCP-TEC-002", "The calendar API refused the call."),
    CALLER_TOKEN_MISSING("MCP-TEC-003", "No caller token reached the tool call, so the calendar API cannot be queried on their behalf.");

    private final String code;
    private final String message;

    TechnicalErrorCode(String code, String message) {
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
