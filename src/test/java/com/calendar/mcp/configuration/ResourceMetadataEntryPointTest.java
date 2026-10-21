package com.calendar.mcp.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.BadCredentialsException;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceMetadataEntryPointTest {

    @Test
    void challenge_shouldPointAtTheMetadataOfThisResource() {
        ResourceMetadataEntryPoint entryPoint = new ResourceMetadataEntryPoint("https://mcp.wely.dev");

        assertThat(entryPoint.challenge())
                .isEqualTo("Bearer resource_metadata=\"https://mcp.wely.dev/.well-known/oauth-protected-resource\"");
    }

    @Test
    void challenge_shouldNotDoubleTheSlashWhenThePublicUrlEndsWithOne() {
        ResourceMetadataEntryPoint entryPoint = new ResourceMetadataEntryPoint("https://mcp.wely.dev/");

        assertThat(entryPoint.challenge()).doesNotContain("dev//");
    }

    @Test
    void commence_shouldAnswer401WithTheChallenge() {
        ResourceMetadataEntryPoint entryPoint = new ResourceMetadataEntryPoint("https://mcp.wely.dev");
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/mcp"));

        StepVerifier.create(entryPoint.commence(exchange, new BadCredentialsException("no token")))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE))
                .contains("resource_metadata=");
    }
}
