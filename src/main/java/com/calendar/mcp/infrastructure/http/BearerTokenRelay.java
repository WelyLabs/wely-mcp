package com.calendar.mcp.infrastructure.http;

import com.calendar.mcp.exception.TechnicalErrorCode;
import com.calendar.mcp.exception.TechnicalException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import reactor.core.publisher.Mono;

/**
 * Carries the caller's own token to the gateway, so the calendar answers as them and this service
 * needs no identity of its own. It never falls back to an anonymous call: without a token the
 * exchange fails, because a silent anonymous call would read someone else's calendar — nobody's.
 */
public final class BearerTokenRelay {

    private BearerTokenRelay() {
    }

    public static ExchangeFilterFunction relayCallerToken() {
        return (request, next) -> callerToken()
                .map(token -> ClientRequest.from(request)
                        .headers(headers -> headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                        .build())
                .flatMap(next::exchange);
    }

    private static Mono<String> callerToken() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .map(authentication -> authentication.getToken().getTokenValue())
                .switchIfEmpty(Mono.error(() -> new TechnicalException(TechnicalErrorCode.CALLER_TOKEN_MISSING)));
    }

    /** Exposed for the test that proves an unauthenticated exchange is refused rather than sent. */
    static Mono<String> callerTokenFor(Authentication authentication) {
        return callerToken().contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
    }
}
