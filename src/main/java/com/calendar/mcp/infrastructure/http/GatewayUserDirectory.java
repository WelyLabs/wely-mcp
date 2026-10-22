package com.calendar.mcp.infrastructure.http;

import com.calendar.mcp.domain.ports.UserDirectory;
import com.calendar.mcp.exception.TechnicalErrorCode;
import com.calendar.mcp.exception.TechnicalException;
import com.calendar.mcp.infrastructure.http.dtos.GatewayFriendProfileResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
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
                    // 403 for a profile that is not the caller's to read and 404 for a stale id are
                    // answers, not failures: that person simply stays unnamed. A 5xx is a broken
                    // dependency and says so -- swallowing it would make wely-users being down
                    // indistinguishable from a friend without a profile.
                    if (response.statusCode().is4xxClientError()) {
                        return response.releaseBody().then(Mono.empty());
                    }
                    if (response.statusCode().isError()) {
                        return response.releaseBody()
                                .then(Mono.<GatewayFriendProfileResponse>error(
                                        new TechnicalException(TechnicalErrorCode.GATEWAY_REFUSED)));
                    }
                    return response.bodyToMono(GatewayFriendProfileResponse.class);
                })
                .mapNotNull(GatewayFriendProfileResponse::displayName)
                .onErrorMap(WebClientRequestException.class, cause ->
                        new TechnicalException(TechnicalErrorCode.GATEWAY_UNREACHABLE, cause));
    }
}
