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

    /** A close friend's own circle; 403 for a plain friend, which reads here as "nobody". */
    static final String CIRCLE_PATH = "/api/v1/social-service/relationships/{userId}/friends";

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

    /**
     * Empty rather than an error when the caller is not close enough: the tool then offers nobody,
     * which is the truth, instead of failing a question that was reasonable to ask.
     */
    @Override
    public Flux<Friend> friendsOf(String userId) {
        return webClient.get()
                .uri(CIRCLE_PATH, userId)
                .exchangeToFlux(response -> {
                    if (response.statusCode().isError()) {
                        return response.releaseBody().thenMany(Flux.<GatewayFriendSummaryResponse>empty());
                    }
                    return response.bodyToFlux(GatewayFriendSummaryResponse.class);
                })
                .map(friend -> new Friend(friend.userId(), friend.userName(), friend.hashtag(), null, null))
                .onErrorResume(cause -> Flux.empty());
    }
}
