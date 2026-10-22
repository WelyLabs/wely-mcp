package com.calendar.mcp.infrastructure.http;

import com.calendar.mcp.domain.models.Friend;
import com.calendar.mcp.domain.ports.SocialGraph;
import com.calendar.mcp.exception.TechnicalErrorCode;
import com.calendar.mcp.exception.TechnicalException;
import com.calendar.mcp.infrastructure.http.dtos.GatewayFriendSummaryResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Flux;

/** Reads the friendship graph through the gateway, with the caller's own token. */
public class GatewaySocialGraph implements SocialGraph {

    /** stripPrefix(2) at the gateway turns this into /social-service/relationships. */
    static final String RELATIONSHIPS_PATH = "/api/v1/social-service/relationships";

    private final WebClient webClient;

    public GatewaySocialGraph(WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public Flux<Friend> friends() {
        return webClient.get()
                .uri(RELATIONSHIPS_PATH)
                .exchangeToFlux(response -> {
                    if (response.statusCode().isError()) {
                        return response.releaseBody()
                                .thenMany(Flux.<GatewayFriendSummaryResponse>error(
                                        new TechnicalException(TechnicalErrorCode.GATEWAY_REFUSED)));
                    }
                    return response.bodyToFlux(GatewayFriendSummaryResponse.class);
                })
                .map(friend -> new Friend(friend.userId(), friend.userName(), friend.hashtag(), null,
                        friend.closeness()))
                .onErrorMap(WebClientRequestException.class, cause ->
                        new TechnicalException(TechnicalErrorCode.GATEWAY_UNREACHABLE, cause));
    }
}
