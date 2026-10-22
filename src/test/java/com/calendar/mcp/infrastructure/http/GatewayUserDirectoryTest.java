package com.calendar.mcp.infrastructure.http;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import com.calendar.mcp.exception.TechnicalErrorCode;
import com.calendar.mcp.exception.TechnicalException;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayUserDirectoryTest {

    private static GatewayUserDirectory directoryAnswering(ExchangeFunction exchangeFunction) {
        return new GatewayUserDirectory(WebClient.builder()
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
    void displayName_shouldPreferTheRealName() {
        GatewayUserDirectory directory = directoryAnswering(request -> Mono.just(json(HttpStatus.OK,
                "{\"userName\":\"mad\",\"firstName\":\"Madeline\",\"lastName\":\"Heyraud\"}")));

        StepVerifier.create(directory.displayName("u1"))
                .expectNext("Madeline Heyraud")
                .verifyComplete();
    }

    @Test
    void displayName_shouldFallBackToTheHandleWhenNoNameIsSet() {
        GatewayUserDirectory directory = directoryAnswering(request -> Mono.just(json(HttpStatus.OK,
                "{\"userName\":\"mad\",\"firstName\":null,\"lastName\":null}")));

        StepVerifier.create(directory.displayName("u1"))
                .expectNext("mad")
                .verifyComplete();
    }

    @Test
    void displayName_shouldUseWhicheverNameIsSet() {
        GatewayUserDirectory directory = directoryAnswering(request -> Mono.just(json(HttpStatus.OK,
                "{\"userName\":\"mad\",\"firstName\":\"Madeline\",\"lastName\":null}")));

        StepVerifier.create(directory.displayName("u1"))
                .expectNext("Madeline")
                .verifyComplete();
    }

    @Test
    void displayName_shouldBeEmptyWhenNothingNamesThem() {
        GatewayUserDirectory directory = directoryAnswering(request -> Mono.just(json(HttpStatus.OK,
                "{\"userName\":null,\"firstName\":null,\"lastName\":null}")));

        StepVerifier.create(directory.displayName("u1")).verifyComplete();
    }

    @Test
    void displayName_shouldStayEmptyOnAProfileTheCallerMayNotRead() {
        GatewayUserDirectory directory = directoryAnswering(request ->
                Mono.just(json(HttpStatus.FORBIDDEN, "{\"title\":\"Forbidden\"}")));

        StepVerifier.create(directory.displayName("u1")).verifyComplete();
    }

    @Test
    void displayName_shouldStayEmptyOnAnUnknownId() {
        GatewayUserDirectory directory = directoryAnswering(request ->
                Mono.just(json(HttpStatus.NOT_FOUND, "{\"title\":\"Not found\"}")));

        StepVerifier.create(directory.displayName("u1")).verifyComplete();
    }

    @Test
    void displayName_shouldFailWhenTheDirectoryIsDown() {
        // A 5xx is a broken dependency: swallowing it would make wely-users being down
        // indistinguishable from a friend who simply has no profile.
        GatewayUserDirectory directory = directoryAnswering(request ->
                Mono.just(json(HttpStatus.BAD_GATEWAY, "{}")));

        StepVerifier.create(directory.displayName("u1"))
                .verifyErrorSatisfies(error -> assertThat(((TechnicalException) error).getErrorCode())
                        .isEqualTo(TechnicalErrorCode.GATEWAY_REFUSED));
    }

    @Test
    void displayName_shouldFailWhenTheGatewayCannotBeReached() {
        GatewayUserDirectory directory = directoryAnswering(request ->
                Mono.error(new WebClientRequestException(new java.io.IOException("connection reset"),
                        org.springframework.http.HttpMethod.GET, java.net.URI.create("http://gateway.test"),
                        new org.springframework.http.HttpHeaders())));

        StepVerifier.create(directory.displayName("u1"))
                .verifyErrorSatisfies(error -> assertThat(((TechnicalException) error).getErrorCode())
                        .isEqualTo(TechnicalErrorCode.GATEWAY_UNREACHABLE));
    }

    @Test
    void displayName_shouldAskForThatUsersProfile() {
        AtomicReference<String> path = new AtomicReference<>();
        GatewayUserDirectory directory = directoryAnswering(request -> {
            path.set(request.url().getPath());
            return Mono.just(json(HttpStatus.OK, "{\"userName\":\"mad\"}"));
        });

        StepVerifier.create(directory.displayName("abc-123")).expectNextCount(1).verifyComplete();

        assertThat(path.get()).isEqualTo("/api/v1/user-service/users/abc-123/profile");
    }
}
