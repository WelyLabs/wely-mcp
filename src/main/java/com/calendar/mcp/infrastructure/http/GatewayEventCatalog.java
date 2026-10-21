package com.calendar.mcp.infrastructure.http;

import com.calendar.mcp.domain.models.EventSummary;
import com.calendar.mcp.domain.ports.EventCatalog;
import com.calendar.mcp.exception.TechnicalErrorCode;
import com.calendar.mcp.exception.TechnicalException;
import com.calendar.mcp.infrastructure.http.dtos.GatewayEventResponse;
import com.calendar.mcp.infrastructure.http.mappers.GatewayEventMapper;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Flux;

import java.time.Instant;

/** Reads the calendar through the gateway, as any other client of the public API would. */
public class GatewayEventCatalog implements EventCatalog {

    /** stripPrefix(2) at the gateway turns this into /events-service/events/me/subscribed. */
    static final String SUBSCRIBED_EVENTS_PATH = "/api/v1/events-service/events/me/subscribed";

    private final WebClient webClient;
    private final GatewayEventMapper mapper;

    public GatewayEventCatalog(WebClient webClient, GatewayEventMapper mapper) {
        this.webClient = webClient;
        this.mapper = mapper;
    }

    @Override
    public Flux<EventSummary> subscribedEvents(Instant from, Instant to) {
        return webClient.get()
                .uri(builder -> builder.path(SUBSCRIBED_EVENTS_PATH)
                        .queryParam("from", from)
                        .queryParam("to", to)
                        .build())
                .exchangeToFlux(response -> {
                    if (response.statusCode().isError()) {
                        // The status is all the domain needs: it reacts to "the calendar refused", not to 403 versus 404.
                        return response.releaseBody()
                                .thenMany(Flux.<GatewayEventResponse>error(
                                        new TechnicalException(TechnicalErrorCode.GATEWAY_REFUSED)));
                    }
                    return response.bodyToFlux(GatewayEventResponse.class);
                })
                .map(mapper::toDomain)
                .onErrorMap(WebClientRequestException.class, cause ->
                        new TechnicalException(TechnicalErrorCode.GATEWAY_UNREACHABLE, cause));
    }
}
