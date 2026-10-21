package com.calendar.mcp.application.rest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * RFC 9728 protected resource metadata, which is how an MCP client discovers where to get a token.
 * Without it a client meeting our 401 has nowhere to go and cannot start a sign-in at all.
 *
 * <p>Both paths are served: the bare one, and the one with the resource's own path appended, which
 * is what a client reading {@code resource_metadata} for {@code /mcp} asks for.
 */
@RestController
public class ProtectedResourceMetadataController {

    private final String resource;
    private final String issuer;

    public ProtectedResourceMetadataController(@Value("${mcp.public-url}") String resource,
                                               @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer) {
        this.resource = resource;
        this.issuer = issuer;
    }

    @GetMapping({"/.well-known/oauth-protected-resource", "/.well-known/oauth-protected-resource/mcp"})
    public Map<String, Object> metadata() {
        return Map.of(
                "resource", resource,
                "authorization_servers", List.of(issuer),
                "bearer_methods_supported", List.of("header"),
                "scopes_supported", List.of("openid", "profile"));
    }
}
