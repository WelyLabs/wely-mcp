package com.calendar.mcp.configuration;

import com.calendar.mcp.application.mappers.EventSummaryToolMapper;
import com.calendar.mcp.application.mappers.FriendToolMapper;
import com.calendar.mcp.application.mcp.CalendarTools;
import com.calendar.mcp.domain.ports.EventCatalog;
import com.calendar.mcp.domain.ports.SocialGraph;
import com.calendar.mcp.domain.ports.UserDirectory;
import com.calendar.mcp.domain.services.EventCreationService;
import com.calendar.mcp.domain.services.EventInvitationService;
import com.calendar.mcp.domain.services.FriendsService;
import com.calendar.mcp.domain.services.MyEventsService;
import com.calendar.mcp.infrastructure.http.GatewayEventCatalog;
import com.calendar.mcp.infrastructure.http.GatewaySocialGraph;
import com.calendar.mcp.infrastructure.http.GatewayUserDirectory;
import com.calendar.mcp.infrastructure.http.mappers.GatewayEventMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Wires the domain by hand: the service and the adapters are plain objects, which is what makes the
 * dependency inversion real and the domain testable without Spring.
 */
@Configuration
public class McpApplicationConfig {

    @Bean
    public EventCatalog eventCatalog(WebClient gatewayWebClient, GatewayEventMapper mapper) {
        return new GatewayEventCatalog(gatewayWebClient, mapper);
    }

    @Bean
    public UserDirectory userDirectory(WebClient gatewayWebClient) {
        return new GatewayUserDirectory(gatewayWebClient);
    }

    @Bean
    public MyEventsService myEventsService(EventCatalog eventCatalog, UserDirectory userDirectory) {
        return new MyEventsService(eventCatalog, userDirectory);
    }

    @Bean
    public EventCreationService eventCreationService(EventCatalog eventCatalog) {
        return new EventCreationService(eventCatalog);
    }

    @Bean
    public SocialGraph socialGraph(WebClient gatewayWebClient) {
        return new GatewaySocialGraph(gatewayWebClient);
    }

    @Bean
    public FriendsService friendsService(SocialGraph socialGraph, UserDirectory userDirectory) {
        return new FriendsService(socialGraph, userDirectory);
    }

    @Bean
    public EventInvitationService eventInvitationService(EventCatalog eventCatalog, FriendsService friendsService) {
        return new EventInvitationService(eventCatalog, friendsService);
    }

    @Bean
    public CalendarTools calendarTools(MyEventsService myEventsService, EventCreationService eventCreationService,
                                       FriendsService friendsService, EventInvitationService eventInvitationService,
                                       EventSummaryToolMapper mapper, FriendToolMapper friendMapper) {
        return new CalendarTools(myEventsService, eventCreationService, friendsService, eventInvitationService,
                mapper, friendMapper);
    }

    // No ToolCallbackProvider bean: the server's annotation scanner finds the @McpTool methods on
    // the CalendarTools bean, and the async provider subscribes their Mono. Declaring a
    // MethodToolCallbackProvider as well would publish the same tool twice, through the blocking
    // path. The boot log's "Registered tools: 1" is what confirms which one is in play.
}
