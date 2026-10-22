package com.calendar.mcp.infrastructure.http.dtos;

import java.time.Instant;
import java.util.List;

/**
 * The slice of wely-events' EventResponse this service reads. Unknown fields are ignored, so the
 * calendar API may grow without breaking this client.
 */
public record GatewayEventResponse(
        String id,
        String title,
        Instant startDate,
        Instant endDate,
        String location,
        /** Boxed on purpose: a primitive makes Jackson fail outright when the field is absent. */
        Boolean allDay,
        /** Null on a birthday, says the API; an empty list is the same thing to us. */
        List<GatewayFriendGoingResponse> friendsGoing,
        Integer friendsGoingCount,
        /** STANDARD, SURPRISE or BIRTHDAY; null on a payload that predates it. */
        String kind,
        /** Whose birthday a SURPRISE celebrates, null on anything else. */
        String honoreeId) {
}
