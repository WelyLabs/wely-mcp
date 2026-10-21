package com.calendar.mcp.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    /** Deny by default: /mcp needs a valid JWT, exactly as the REST services do. */
    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http,
                                                            @Value("${mcp.public-url}") String publicUrl) {
        ResourceMetadataEntryPoint entryPoint = new ResourceMetadataEntryPoint(publicUrl);
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        // Kubernetes probes: the kubelet carries no token. Only the two health groups.
                        .pathMatchers(HttpMethod.GET, "/actuator/health/liveness", "/actuator/health/readiness").permitAll()
                        // Discovery has to be readable without a token: it is what a client reads
                        // *because* it has none. It carries no user data, only our issuer's address.
                        .pathMatchers(HttpMethod.GET, "/.well-known/**").permitAll()
                        .anyExchange().authenticated())
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(entryPoint))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(entryPoint))
                .build();
    }
}
