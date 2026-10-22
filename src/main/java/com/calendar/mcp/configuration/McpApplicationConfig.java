package com.calendar.mcp.configuration;

import com.calendar.mcp.application.mappers.EventSummaryToolMapper;
import com.calendar.mcp.application.mappers.FriendToolMapper;
import com.calendar.mcp.application.mcp.CalendarTools;
import com.calendar.mcp.domain.ports.EventCatalog;
import com.calendar.mcp.domain.ports.EventWriter;
import com.calendar.mcp.domain.ports.SocialGraph;
import com.calendar.mcp.domain.ports.UserDirectory;
import com.calendar.mcp.domain.services.EventAttendanceService;
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

    /**
     * One bean for the one adapter. It implements both ports, so an EventCatalog or an EventWriter
     * parameter resolves to it on its own -- republishing it under each interface would leave two
     * beans of the same concrete type and make injection by that type ambiguous.
     */
    @Bean
    public GatewayEventCatalog gatewayEventCatalog(WebClient gatewayWebClient, GatewayEventMapper mapper) {
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
    public EventCreationService eventCreationService(EventWriter eventWriter) {
        return new EventCreationService(eventWriter);
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
    public EventInvitationService eventInvitationService(EventWriter eventWriter, FriendsService friendsService) {
        return new EventInvitationService(eventWriter, friendsService);
    }

    @Bean
    public EventAttendanceService eventAttendanceService(EventCatalog eventCatalog, EventWriter eventWriter,
                                                        SocialGraph socialGraph) {
        return new EventAttendanceService(eventCatalog, eventWriter, socialGraph);
    }

    @Bean
    public CalendarTools calendarTools(MyEventsService myEventsService, EventCreationService eventCreationService,
                                       FriendsService friendsService, EventInvitationService eventInvitationService,
                                       EventAttendanceService eventAttendanceService,
                                       EventSummaryToolMapper mapper, FriendToolMapper friendMapper) {
        return new CalendarTools(myEventsService, eventCreationService, friendsService, eventInvitationService,
                eventAttendanceService, mapper, friendMapper);
    }

    // No ToolCallbackProvider bean: the server's annotation scanner finds the @McpTool methods on
    // the CalendarTools bean, and the async provider subscribes their Mono. Declaring a
    // MethodToolCallbackProvider as well would publish the same tool twice, through the blocking
    // path. The boot log's "Registered tools: 1" is what confirms which one is in play.
}
