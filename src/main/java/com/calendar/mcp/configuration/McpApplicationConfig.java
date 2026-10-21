package com.calendar.mcp.configuration;

import com.calendar.mcp.application.mappers.EventSummaryToolMapper;
import com.calendar.mcp.application.mcp.CalendarTools;
import com.calendar.mcp.domain.ports.EventCatalog;
import com.calendar.mcp.domain.services.MyEventsService;
import com.calendar.mcp.infrastructure.http.GatewayEventCatalog;
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
    public MyEventsService myEventsService(EventCatalog eventCatalog) {
        return new MyEventsService(eventCatalog);
    }

    @Bean
    public CalendarTools calendarTools(MyEventsService myEventsService, EventSummaryToolMapper mapper) {
        return new CalendarTools(myEventsService, mapper);
    }

    // No ToolCallbackProvider bean: the server's annotation scanner finds the @McpTool methods on
    // the CalendarTools bean, and the async provider subscribes their Mono. Declaring a
    // MethodToolCallbackProvider as well would publish the same tool twice, through the blocking
    // path. The boot log's "Registered tools: 1" is what confirms which one is in play.
}
