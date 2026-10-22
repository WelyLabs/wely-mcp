package com.calendar.mcp.infrastructure.http;

import com.calendar.mcp.domain.models.EventSummary;
import com.calendar.mcp.domain.models.NewEvent;
import com.calendar.mcp.domain.ports.EventCatalog;
import com.calendar.mcp.exception.TechnicalErrorCode;
import com.calendar.mcp.exception.TechnicalException;
import com.calendar.mcp.infrastructure.http.dtos.GatewayEventRequest;
import com.calendar.mcp.infrastructure.http.dtos.GatewayInvitationRequest;
import com.calendar.mcp.infrastructure.http.dtos.GatewayEventResponse;
import com.calendar.mcp.infrastructure.http.mappers.GatewayEventMapper;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

/** Reads the calendar through the gateway, as any other client of the public API would. */
public class GatewayEventCatalog implements EventCatalog {

    /** stripPrefix(2) at the gateway turns this into /events-service/events/me/subscribed. */
    static final String SUBSCRIBED_EVENTS_PATH = "/api/v1/events-service/events/me/subscribed";

    /** Same route without the suffix: POST creates, the organizer being whoever the token names. */
    static final String EVENTS_PATH = "/api/v1/events-service/events";

    /** One invitee per call, as the calendar takes them. */
    static final String INVITATIONS_PATH = "/api/v1/events-service/events/{eventId}/invitations";

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

    @Override
    public Mono<EventSummary> create(NewEvent event) {
        return webClient.post()
                .uri(EVENTS_PATH)
                .bodyValue(new GatewayEventRequest(event.title(), event.startDate(), event.endDate(),
                        event.location(), event.description(), event.allDay()))
                .exchangeToMono(response -> {
                    if (response.statusCode().isError()) {
                        // The calendar's own validation lands here: a date in the past, a title it
                        // refuses. The status is all the domain needs to say the calendar refused.
                        return response.releaseBody()
                                .then(Mono.<GatewayEventResponse>error(
                                        new TechnicalException(TechnicalErrorCode.GATEWAY_REFUSED)));
                    }
                    return response.bodyToMono(GatewayEventResponse.class);
                })
                .map(mapper::toDomain)
                .onErrorMap(WebClientRequestException.class, cause ->
                        new TechnicalException(TechnicalErrorCode.GATEWAY_UNREACHABLE, cause));
    }

    @Override
    public Mono<Void> invite(String eventId, String userId) {
        return webClient.post()
                .uri(INVITATIONS_PATH, eventId)
                .bodyValue(new GatewayInvitationRequest(userId))
                .exchangeToMono(response -> {
                    if (response.statusCode().isError()) {
                        // 403 for someone else's event, 409 when they already take part: the caller
                        // is told which friend it was, by the service that knows the handle.
                        return response.releaseBody()
                                .then(Mono.error(new TechnicalException(TechnicalErrorCode.GATEWAY_REFUSED)));
                    }
                    return response.releaseBody();
                })
                .onErrorMap(WebClientRequestException.class, cause ->
                        new TechnicalException(TechnicalErrorCode.GATEWAY_UNREACHABLE, cause));
    }
}
