package com.calendar.mcp.infrastructure.http;

import com.calendar.mcp.domain.models.Friend;
import com.calendar.mcp.domain.ports.SocialGraph;
import com.calendar.mcp.exception.TechnicalErrorCode;
import com.calendar.mcp.exception.TechnicalException;
import com.calendar.mcp.infrastructure.http.dtos.GatewayFriendSummaryResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Reads the friendship graph through the gateway, with the caller's own token. */
public class GatewaySocialGraph implements SocialGraph {

    /** stripPrefix(2) at the gateway turns this into /social-service/relationships. */
    static final String RELATIONSHIPS_PATH = "/api/v1/social-service/relationships";

    /** The people directory: open to any signed-in caller, friend or not. */
    static final String PERSON_PATH = "/api/v1/social-service/users/{userId}";

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

    /** Empty rather than an error for an id the directory does not know: the asker stays unnamed. */
    @Override
    public Mono<Friend> person(String userId) {
        return webClient.get()
                .uri(PERSON_PATH, userId)
                .exchangeToMono(response -> {
                    if (response.statusCode().isError()) {
                        return response.releaseBody().then(Mono.<GatewayFriendSummaryResponse>empty());
                    }
                    return response.bodyToMono(GatewayFriendSummaryResponse.class);
                })
                .map(person -> new Friend(person.userId(), person.userName(), person.hashtag(), null, null))
                .onErrorResume(cause -> Mono.empty());
    }
}
