package com.calendar.mcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

/**
 * Boots the whole application and speaks MCP to it. This is the test that was missing when the
 * server answered 404 on /mcp for a day: the transport is a separate auto-configuration from the
 * server, and only an authenticated call tells the two apart -- without a token, security answers
 * 401 before routing and a missing route looks exactly like a missing permission.
 *
 * <p>No Docker: nothing here leaves the JVM. The JWK set is never fetched because the token is
 * mocked, and the gateway is never called because initialize touches no tool.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "mcp.public-url=http://localhost:8088",
        "gateway.url=http://localhost:18081",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:9999/realms/wely-realm",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:9999/certs"
})
class McpApplicationTest {

    private static final String INITIALIZE = """
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18",
             "capabilities":{},"clientInfo":{"name":"test","version":"1"}}}
            """;

    private WebTestClient client;

    @Autowired
    private ApplicationContext context;

    @BeforeEach
    void bindToTheRealChain() {
        // Bound to the application context, so the security filters and the MCP router are the
        // deployed ones -- the point of this test is that both are actually wired.
        client = WebTestClient.bindToApplicationContext(context)
                .apply(springSecurity())
                .configureClient()
                .build();
    }

    @Test
    void mcp_shouldAnswerInitializeToAnAuthenticatedCaller() {
        client.mutateWith(mockJwt()).post().uri("/mcp")
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .bodyValue(INITIALIZE)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.result.serverInfo.name").isEqualTo("wely-calendar")
                .jsonPath("$.result.capabilities.tools").exists();
    }

    @Test
    void mcp_shouldRefuseACallerWithoutAToken() {
        client.post().uri("/mcp")
                .header("Content-Type", "application/json")
                .bodyValue(INITIALIZE)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueMatches("WWW-Authenticate", ".*resource_metadata=.*");
    }

    @Test
    void discovery_shouldBeReadableWithoutAToken() {
        client.get().uri("/.well-known/oauth-protected-resource")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.resource").isEqualTo("http://localhost:8088")
                .jsonPath("$.authorization_servers[0]").isEqualTo("http://localhost:9999/realms/wely-realm");
    }

    @Test
    void probes_shouldBeReadableWithoutAToken() {
        client.get().uri("/actuator/health/readiness").exchange().expectStatus().isOk();
    }
}
