package com.calendar.mcp.infrastructure.http.mappers;

import com.calendar.mcp.domain.models.Attendee;
import com.calendar.mcp.domain.models.EventSummary;
import com.calendar.mcp.infrastructure.http.dtos.GatewayEventResponse;
import com.calendar.mcp.infrastructure.http.dtos.GatewayFriendGoingResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface GatewayEventMapper {

    @Mapping(target = "friendsGoing", source = "friendsGoing")
    @Mapping(target = "friendsGoingCount", source = "friendsGoingCount", defaultValue = "0")
    EventSummary toDomain(GatewayEventResponse response);

    /** Named later, by the directory: the calendar answers with ids only. */
    default Attendee toAttendee(GatewayFriendGoingResponse friend) {
        return friend == null ? null : Attendee.unnamed(friend.userId(), friend.closeness());
    }

    default List<Attendee> toAttendees(List<GatewayFriendGoingResponse> friends) {
        return friends == null ? List.of() : friends.stream().map(this::toAttendee).filter(a -> a != null).toList();
    }
}
