package com.calendar.mcp.infrastructure.http.mappers;

import com.calendar.mcp.domain.models.EventSummary;
import com.calendar.mcp.infrastructure.http.dtos.GatewayEventResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface GatewayEventMapper {

    EventSummary toDomain(GatewayEventResponse response);
}
