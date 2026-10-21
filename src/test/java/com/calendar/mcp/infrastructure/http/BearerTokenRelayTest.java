package com.calendar.mcp.infrastructure.http;

import com.calendar.mcp.exception.TechnicalErrorCode;
import com.calendar.mcp.exception.TechnicalException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class BearerTokenRelayTest {

    private static JwtAuthenticationToken callerWith(String tokenValue) {
        Jwt jwt = Jwt.withTokenValue(tokenValue).header("alg", "none").claim("businessId", "u1").build();
        return new JwtAuthenticationToken(jwt);
    }

    private static ClientRequest anyRequest() {
        return ClientRequest.create(HttpMethod.GET, URI.create("http://gateway.test/api/v1/events-service/events")).build();
    }

    @Test
    void relayCallerToken_shouldSendTheCallersOwnToken() {
        AtomicReference<String> sent = new AtomicReference<>();
        ExchangeFilterFunction filter = BearerTokenRelay.relayCallerToken();

        Mono<ClientResponse> exchange = filter.filter(anyRequest(), request -> {
            sent.set(request.headers().getFirst(HttpHeaders.AUTHORIZATION));
            return Mono.just(ClientResponse.create(HttpStatus.OK).build());
        }).contextWrite(ReactiveSecurityContextHolder.withAuthentication(callerWith("abc.def.ghi")));

        StepVerifier.create(exchange).expectNextCount(1).verifyComplete();
        assertThat(sent.get()).isEqualTo("Bearer abc.def.ghi");
    }

    @Test
    void relayCallerToken_shouldNotSendTheRequestWhenNoTokenIsInContext() {
        AtomicReference<Boolean> reached = new AtomicReference<>(false);
        ExchangeFilterFunction filter = BearerTokenRelay.relayCallerToken();

        Mono<ClientResponse> exchange = filter.filter(anyRequest(), request -> {
            reached.set(true);
            return Mono.just(ClientResponse.create(HttpStatus.OK).build());
        });

        StepVerifier.create(exchange)
                .verifyErrorSatisfies(error -> assertThat(((TechnicalException) error).getErrorCode())
                        .isEqualTo(TechnicalErrorCode.CALLER_TOKEN_MISSING));
        assertThat(reached.get()).isFalse();
    }

    @Test
    void relayCallerToken_shouldRefuseAnAuthenticationThatCarriesNoJwt() {
        ExchangeFilterFunction filter = BearerTokenRelay.relayCallerToken();

        Mono<ClientResponse> exchange = filter
                .filter(anyRequest(), request -> Mono.just(ClientResponse.create(HttpStatus.OK).build()))
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                        new TestingAuthenticationToken("someone", "secret")));

        StepVerifier.create(exchange).verifyError(TechnicalException.class);
    }

    @Test
    void callerTokenFor_shouldReadTheTokenValueOutOfTheAuthentication() {
        StepVerifier.create(BearerTokenRelay.callerTokenFor(callerWith("jwt-value")))
                .expectNext("jwt-value")
                .verifyComplete();
    }
}
