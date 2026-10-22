package com.calendar.mcp.domain.ports;

import com.calendar.mcp.domain.models.Friend;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Who the caller is friends with. */
public interface SocialGraph {

    /** The caller's accepted friends, pending requests excluded. */
    Flux<Friend> friends();

    /**
     * Anyone's handle, friend or not: the people directory is open to every signed-in caller, which
     * is what lets an organizer name someone who asked to join without being their friend.
     */
    Mono<Friend> person(String userId);
}
