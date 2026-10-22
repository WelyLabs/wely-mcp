package com.calendar.mcp.application.mappers;

import com.calendar.mcp.application.dtos.FriendResponse;
import com.calendar.mcp.application.dtos.InvitationResponse;
import com.calendar.mcp.domain.models.Friend;
import com.calendar.mcp.domain.models.Invitation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface FriendToolMapper {

    /** The name falls back to the handle: a friend is always nameable, even unread. */
    @Mapping(target = "handle", expression = "java(friend.handle())")
    @Mapping(target = "name", expression = "java(friend.name() != null ? friend.name() : friend.handle())")
    FriendResponse toResponse(Friend friend);

    @Mapping(target = "friend", source = "handle")
    @Mapping(target = "invited", source = "sent")
    InvitationResponse toResponse(Invitation invitation);
}
