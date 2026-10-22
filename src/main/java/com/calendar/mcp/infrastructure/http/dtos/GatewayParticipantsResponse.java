package com.calendar.mcp.infrastructure.http.dtos;

import java.util.List;

/**
 * Who takes part in an event and who asked to. The calendar fills {@code participantIds} only for
 * someone involved, so a non-empty list is how this service knows the caller is in.
 */
public record GatewayParticipantsResponse(
        int count,
        List<String> participantIds,
        List<String> requestedIds) {

    public boolean takingPart() {
        return participantIds != null && !participantIds.isEmpty();
    }
}
