package com.calendar.mcp.infrastructure.http;

import com.calendar.mcp.domain.ports.UserDirectory;
import com.calendar.mcp.infrastructure.http.dtos.GatewayFriendProfileResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/** Names a user through the gateway, with the caller's own token: a profile they may not read stays unnamed. */
public class GatewayUserDirectory implements UserDirectory {

    /** stripPrefix(2) at the gateway turns this into /user-service/users/{id}/profile. */
    static final String PROFILE_PATH = "/api/v1/user-service/users/{userId}/profile";

    private final WebClient webClient;

    public GatewayUserDirectory(WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public Mono<String> displayName(String userId) {
        return webClient.get()
                .uri(PROFILE_PATH, userId)
                .exchangeToMono(response -> {
                    if (response.statusCode().isError()) {
                        // 403 for a profile that is not the caller's to read, 404 for a stale id,
                        // 502 when wely-users is down: none of them is worth failing the answer for.
                        return response.releaseBody().then(Mono.empty());
                    }
                    return response.bodyToMono(GatewayFriendProfileResponse.class);
                })
                .mapNotNull(GatewayFriendProfileResponse::displayName)
                .onErrorResume(cause -> Mono.empty());
    }
}
