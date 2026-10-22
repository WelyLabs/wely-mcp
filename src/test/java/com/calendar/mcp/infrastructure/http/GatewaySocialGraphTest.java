package com.calendar.mcp.infrastructure.http;

import com.calendar.mcp.domain.models.Friend;
import com.calendar.mcp.exception.TechnicalErrorCode;
import com.calendar.mcp.exception.TechnicalException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class GatewaySocialGraphTest {

    private static final String ONE_FRIEND = """
            [{"userId":"u1","userName":"camille","hashtag":1234,"closeness":"CLOSE",
              "profilePicUrl":"https://ignored","proposal":null,"proposedByFriend":false}]
            """;

    private static GatewaySocialGraph graphAnswering(ExchangeFunction exchangeFunction) {
        return new GatewaySocialGraph(WebClient.builder()
                .baseUrl("http://gateway.test")
                .exchangeFunction(exchangeFunction)
                .build());
    }

    private static ClientResponse json(HttpStatus status, String body) {
        return ClientResponse.create(status)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();
    }

    @Test
    void friends_shouldMapWhatTheSocialServiceReturns() {
        StepVerifier.create(graphAnswering(request -> Mono.just(json(HttpStatus.OK, ONE_FRIEND))).friends())
                .assertNext(friend -> {
                    assertThat(friend.userId()).isEqualTo("u1");
                    assertThat(friend.handle()).isEqualTo("camille#1234");
                    assertThat(friend.closeness()).isEqualTo("CLOSE");
                    assertThat(friend.name()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void friends_shouldAskTheRelationshipsRoute() {
        AtomicReference<String> path = new AtomicReference<>();
        GatewaySocialGraph graph = graphAnswering(request -> {
            path.set(request.url().getPath());
            return Mono.just(json(HttpStatus.OK, "[]"));
        });

        StepVerifier.create(graph.friends()).verifyComplete();

        assertThat(path.get()).isEqualTo(GatewaySocialGraph.RELATIONSHIPS_PATH);
    }

    @Test
    void friends_shouldCompleteWhenTheCallerHasNone() {
        StepVerifier.create(graphAnswering(request -> Mono.just(json(HttpStatus.OK, "[]"))).friends())
                .verifyComplete();
    }

    @Test
    void friends_shouldTranslateARefusalIntoATechnicalFailure() {
        StepVerifier.create(graphAnswering(request -> Mono.just(json(HttpStatus.FORBIDDEN, "{}"))).friends())
                .verifyErrorSatisfies(error -> assertThat(((TechnicalException) error).getErrorCode())
                        .isEqualTo(TechnicalErrorCode.GATEWAY_REFUSED));
    }

    @Test
    void friends_shouldKeepAFriendWithoutAHashtag() {
        String noHashtag = "[{\"userId\":\"u1\",\"userName\":\"camille\",\"hashtag\":null,\"closeness\":\"FRIEND\"}]";

        StepVerifier.create(graphAnswering(request -> Mono.just(json(HttpStatus.OK, noHashtag))).friends()
                        .map(Friend::handle))
                .expectNext("camille")
                .verifyComplete();
    }

    @Test
    void friendsOf_shouldMapACloseFriendsCircle() {
        StepVerifier.create(graphAnswering(request -> Mono.just(json(HttpStatus.OK, ONE_FRIEND))).friendsOf("u2"))
                .assertNext(friend -> {
                    assertThat(friend.handle()).isEqualTo("camille#1234");
                    assertThat(friend.closeness()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void friendsOf_shouldAskThatFriendsCircle() {
        AtomicReference<String> path = new AtomicReference<>();
        GatewaySocialGraph graph = graphAnswering(request -> {
            path.set(request.url().getPath());
            return Mono.just(json(HttpStatus.OK, "[]"));
        });

        StepVerifier.create(graph.friendsOf("u2")).verifyComplete();

        assertThat(path.get()).isEqualTo("/api/v1/social-service/relationships/u2/friends");
    }

    @Test
    void friendsOf_shouldOfferNobodyWhenTheCallerIsNotCloseEnough() {
        StepVerifier.create(graphAnswering(request -> Mono.just(json(HttpStatus.FORBIDDEN, "{}"))).friendsOf("u2"))
                .verifyComplete();
    }

    @Test
    void friendsOf_shouldOfferNobodyWhenTheSocialServiceIsDown() {
        StepVerifier.create(graphAnswering(request -> Mono.error(new IllegalStateException("down"))).friendsOf("u2"))
                .verifyComplete();
    }
}
