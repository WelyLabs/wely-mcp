package com.calendar.mcp.configuration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Answers an unauthenticated call with the pointer a client needs to sign in: a plain
 * {@code WWW-Authenticate: Bearer} tells it only that it failed, while {@code resource_metadata}
 * tells it where to find the authorization server (RFC 9728, as MCP clients expect).
 */
public class ResourceMetadataEntryPoint implements ServerAuthenticationEntryPoint {

    static final String METADATA_PATH = "/.well-known/oauth-protected-resource";

    private final String challenge;

    public ResourceMetadataEntryPoint(String publicUrl) {
        this.challenge = "Bearer resource_metadata=\"%s%s\"".formatted(trimTrailingSlash(publicUrl), METADATA_PATH);
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException exception) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, challenge);
        return exchange.getResponse().setComplete();
    }

    String challenge() {
        return challenge;
    }
}
