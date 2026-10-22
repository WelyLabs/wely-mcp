package com.calendar.mcp.infrastructure.http;

import com.calendar.mcp.domain.models.EventSummary;
import com.calendar.mcp.domain.models.NewEvent;
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

    private static final Instant FROM = Instant.parse("2026-10-21T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-10-22T00:00:00Z");

    private static final String ONE_EVENT = """
            [{"id":"e1","title":"Dinner","startDate":"2026-10-21T19:00:00Z",
              "endDate":"2026-10-21T22:00:00Z","location":"Niort","allDay":false,
              "friendsGoing":[{"userId":"f1","closeness":"CLOSE"}],"friendsGoingCount":3,
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

        StepVerifier.create(catalog.subscribedEvents(FROM, TO))
                .assertNext(event -> {
                    assertThat(event.id()).isEqualTo("e1");
                    assertThat(event.title()).isEqualTo("Dinner");
                    assertThat(event.startDate()).isEqualTo(Instant.parse("2026-10-21T19:00:00Z"));
                    assertThat(event.location()).isEqualTo("Niort");
                    assertThat(event.friendsGoing()).singleElement().satisfies(friend -> {
                        assertThat(friend.userId()).isEqualTo("f1");
                        assertThat(friend.closeness()).isEqualTo("CLOSE");
                        assertThat(friend.isNamed()).isFalse();
                    });
                    assertThat(event.friendsGoingCount()).isEqualTo(3);
                })
                .verifyComplete();
    }

    @Test
    void subscribedEvents_shouldIgnoreFieldsItDoesNotRead() {
        GatewayEventCatalog catalog = catalogAnswering(request -> Mono.just(json(HttpStatus.OK, ONE_EVENT)));

        StepVerifier.create(catalog.subscribedEvents(FROM, TO))
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

        StepVerifier.create(catalog.subscribedEvents(FROM, TO)).verifyComplete();

        assertThat(called.get()).isEqualTo(GatewayEventCatalog.SUBSCRIBED_EVENTS_PATH);
    }

    @Test
    void subscribedEvents_shouldSendTheRangeAsQueryParameters() {
        AtomicReference<String> query = new AtomicReference<>();
        GatewayEventCatalog catalog = catalogAnswering(request -> {
            query.set(request.url().getQuery());
            return Mono.just(json(HttpStatus.OK, "[]"));
        });

        StepVerifier.create(catalog.subscribedEvents(FROM, TO)).verifyComplete();

        assertThat(query.get()).contains("from=2026-10-21T00:00:00Z").contains("to=2026-10-22T00:00:00Z");
    }

    @Test
    void subscribedEvents_shouldCompleteOnAnEmptyCalendar() {
        GatewayEventCatalog catalog = catalogAnswering(request -> Mono.just(json(HttpStatus.OK, "[]")));

        StepVerifier.create(catalog.subscribedEvents(FROM, TO)).verifyComplete();
    }

    @Test
    void create_shouldPostToTheEventsRouteAndMapTheAnswer() {
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> method = new AtomicReference<>();
        GatewayEventCatalog catalog = catalogAnswering(request -> {
            path.set(request.url().getPath());
            method.set(request.method().name());
            return Mono.just(json(HttpStatus.CREATED,
                    "{\"id\":\"new-id\",\"title\":\"Dîner\",\"startDate\":\"2026-10-24T18:30:00Z\"}"));
        });

        StepVerifier.create(catalog.create(new NewEvent("Dîner", Instant.parse("2026-10-24T18:30:00Z"), null,
                        "Niort", null, false)))
                .assertNext(event -> assertThat(event.id()).isEqualTo("new-id"))
                .verifyComplete();

        assertThat(method.get()).isEqualTo("POST");
        assertThat(path.get()).isEqualTo(GatewayEventCatalog.EVENTS_PATH);
    }

    @Test
    void create_shouldTranslateARefusalIntoATechnicalFailure() {
        GatewayEventCatalog catalog = catalogAnswering(request ->
                Mono.just(json(HttpStatus.BAD_REQUEST, "{\"title\":\"Event in the past\"}")));

        StepVerifier.create(catalog.create(new NewEvent("Dîner", Instant.parse("2020-01-01T10:00:00Z"), null,
                        "Niort", null, false)))
                .verifyErrorSatisfies(error -> assertThat(((TechnicalException) error).getErrorCode())
                        .isEqualTo(TechnicalErrorCode.GATEWAY_REFUSED));
    }

    @Test
    void guestPolicyOf_shouldNameTheHonoreeOnASurprise() {
        GatewayEventCatalog catalog = catalogAnswering(request -> Mono.just(json(HttpStatus.OK,
                "{\"id\":\"e1\",\"kind\":\"SURPRISE\",\"honoreeId\":\"u2\"}")));

        StepVerifier.create(catalog.guestPolicyOf("e1"))
                .assertNext(policy -> {
                    assertThat(policy.surprise()).isTrue();
                    assertThat(policy.honoreeId()).isEqualTo("u2");
                })
                .verifyComplete();
    }

    @Test
    void guestPolicyOf_shouldFallBackToTheOrganizersFriendsOnAnyOtherKind() {
        GatewayEventCatalog catalog = catalogAnswering(request -> Mono.just(json(HttpStatus.OK,
                "{\"id\":\"e1\",\"kind\":\"STANDARD\"}")));

        StepVerifier.create(catalog.guestPolicyOf("e1"))
                .assertNext(policy -> assertThat(policy.namesAnHonoree()).isFalse())
                .verifyComplete();
    }

    @Test
    void guestPolicyOf_shouldFallBackWhenThePayloadHasNoKind() {
        GatewayEventCatalog catalog = catalogAnswering(request ->
                Mono.just(json(HttpStatus.OK, "{\"id\":\"e1\"}")));

        StepVerifier.create(catalog.guestPolicyOf("e1"))
                .assertNext(policy -> assertThat(policy.surprise()).isFalse())
                .verifyComplete();
    }

    @Test
    void guestPolicyOf_shouldTranslateARefusalIntoATechnicalFailure() {
        GatewayEventCatalog catalog = catalogAnswering(request ->
                Mono.just(json(HttpStatus.NOT_FOUND, "{}")));

        StepVerifier.create(catalog.guestPolicyOf("e1"))
                .verifyErrorSatisfies(error -> assertThat(((TechnicalException) error).getErrorCode())
                        .isEqualTo(TechnicalErrorCode.GATEWAY_REFUSED));
    }

    @Test
    void invite_shouldPostTheFriendToTheInvitationsRouteOfThatEvent() {
        AtomicReference<String> path = new AtomicReference<>();
        GatewayEventCatalog catalog = catalogAnswering(request -> {
            path.set(request.url().getPath());
            return Mono.just(ClientResponse.create(HttpStatus.NO_CONTENT).build());
        });

        StepVerifier.create(catalog.invite("event-7", "u1")).verifyComplete();

        assertThat(path.get()).isEqualTo("/api/v1/events-service/events/event-7/invitations");
    }

    @Test
    void invite_shouldTranslateARefusalIntoATechnicalFailure() {
        GatewayEventCatalog catalog = catalogAnswering(request ->
                Mono.just(json(HttpStatus.CONFLICT, "{\"title\":\"Already taking part\"}")));

        StepVerifier.create(catalog.invite("event-7", "u1"))
                .verifyErrorSatisfies(error -> assertThat(((TechnicalException) error).getErrorCode())
                        .isEqualTo(TechnicalErrorCode.GATEWAY_REFUSED));
    }

    @Test
    void subscribedEvents_shouldTranslateARefusalIntoATechnicalFailure() {
        GatewayEventCatalog catalog = catalogAnswering(request ->
                Mono.just(json(HttpStatus.FORBIDDEN, "{\"title\":\"Forbidden\"}")));

        StepVerifier.create(catalog.subscribedEvents(FROM, TO))
                .verifyErrorSatisfies(error -> assertThat(((TechnicalException) error).getErrorCode())
                        .isEqualTo(TechnicalErrorCode.GATEWAY_REFUSED));
    }

    @Test
    void subscribedEvents_shouldTranslateAServerErrorIntoATechnicalFailure() {
        GatewayEventCatalog catalog = catalogAnswering(request ->
                Mono.just(json(HttpStatus.INTERNAL_SERVER_ERROR, "{}")));

        StepVerifier.create(catalog.subscribedEvents(FROM, TO))
                .verifyError(TechnicalException.class);
    }

    @Test
    void subscribedEvents_shouldNotLeakTheWebClientFailure() {
        GatewayEventCatalog catalog = catalogAnswering(request ->
                Mono.error(new IllegalStateException("connection reset")));

        StepVerifier.create(catalog.subscribedEvents(FROM, TO))
                .verifyErrorSatisfies(error -> assertThat(error).isNotInstanceOf(TechnicalException.class));
    }
}
