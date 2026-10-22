package com.calendar.mcp.domain.models;

/** What came of trying to join: a surprise asks the organizer first, anything else lets one in. */
public enum JoinOutcome {

    JOINED,
    REQUESTED,
    ALREADY_TAKING_PART,
    ALREADY_REQUESTED
}
