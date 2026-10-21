package com.calendar.mcp.infrastructure.http;

import com.calendar.mcp.domain.models.EventSummary;
import com.calendar.mcp.exception.TechnicalErrorCode;
import com.calendar.mcp.exception.TechnicalException;
import com.calendar.mcp.infrastructure.http.mappers.GatewayEventMapperImpl;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayEventCatalogTest {

    private static final String ONE_EVENT = """
            [{"id":"e1","title":"Dinner","startDate":"2026-10-21T19:00:00Z",
              "endDate":"2026-10-21T22:00:00Z","location":"Niort","allDay":false,
              "description":"ignored","visibility":"PUBLIC"}]
            """;

    private static GatewayEventCatalog catalogAnswering(ExchangeFunction exchangeFunction) {
        WebClient webClient = WebClient.builder()
                .baseUrl("http://gateway.test")
                .exchangeFunction(exchangeFunction)
                .build();
        return new GatewayEventCatalog(webClient, new GatewayEventMapperImpl());
    }

    private static ClientResponse json(HttpStatus status, String body) {
        return ClientResponse.create(status)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();
    }

    @Test
    void subscribedEvents_shouldMapWhatTheCalendarReturns() {
        GatewayEventCatalog catalog = catalogAnswering(request -> Mono.just(json(HttpStatus.OK, ONE_EVENT)));

        StepVerifier.create(catalog.subscribedEvents())
                .assertNext(event -> {
                    assertThat(event.id()).isEqualTo("e1");
                    assertThat(event.title()).isEqualTo("Dinner");
                    assertThat(event.startDate()).isEqualTo(Instant.parse("2026-10-21T19:00:00Z"));
                    assertThat(event.location()).isEqualTo("Niort");
                })
                .verifyComplete();
    }

    @Test
    void subscribedEvents_shouldIgnoreFieldsItDoesNotRead() {
        GatewayEventCatalog catalog = catalogAnswering(request -> Mono.just(json(HttpStatus.OK, ONE_EVENT)));

        StepVerifier.create(catalog.subscribedEvents())
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void subscribedEvents_shouldCallTheGatewayRouteOfTheEventsService() {
        AtomicReference<String> called = new AtomicReference<>();
        GatewayEventCatalog catalog = catalogAnswering(request -> {
            called.set(request.url().getPath());
            return Mono.just(json(HttpStatus.OK, "[]"));
        });

        StepVerifier.create(catalog.subscribedEvents()).verifyComplete();

        assertThat(called.get()).isEqualTo(GatewayEventCatalog.SUBSCRIBED_EVENTS_PATH);
    }

    @Test
    void subscribedEvents_shouldCompleteOnAnEmptyCalendar() {
        GatewayEventCatalog catalog = catalogAnswering(request -> Mono.just(json(HttpStatus.OK, "[]")));

        StepVerifier.create(catalog.subscribedEvents()).verifyComplete();
    }

    @Test
    void subscribedEvents_shouldTranslateARefusalIntoATechnicalFailure() {
        GatewayEventCatalog catalog = catalogAnswering(request ->
                Mono.just(json(HttpStatus.FORBIDDEN, "{\"title\":\"Forbidden\"}")));

        StepVerifier.create(catalog.subscribedEvents())
                .verifyErrorSatisfies(error -> assertThat(((TechnicalException) error).getErrorCode())
                        .isEqualTo(TechnicalErrorCode.GATEWAY_REFUSED));
    }

    @Test
    void subscribedEvents_shouldTranslateAServerErrorIntoATechnicalFailure() {
        GatewayEventCatalog catalog = catalogAnswering(request ->
                Mono.just(json(HttpStatus.INTERNAL_SERVER_ERROR, "{}")));

        StepVerifier.create(catalog.subscribedEvents())
                .verifyError(TechnicalException.class);
    }

    @Test
    void subscribedEvents_shouldNotLeakTheWebClientFailure() {
        GatewayEventCatalog catalog = catalogAnswering(request ->
                Mono.error(new IllegalStateException("connection reset")));

        StepVerifier.create(catalog.subscribedEvents())
                .verifyErrorSatisfies(error -> assertThat(error).isNotInstanceOf(TechnicalException.class));
    }
}
