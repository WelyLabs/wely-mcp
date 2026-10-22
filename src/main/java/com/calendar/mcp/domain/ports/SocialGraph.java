package com.calendar.mcp.domain.ports;

import com.calendar.mcp.domain.models.Friend;
import reactor.core.publisher.Flux;

/** Who the caller is friends with. */
public interface SocialGraph {

    /** The caller's accepted friends, pending requests excluded. */
    Flux<Friend> friends();

    /**
     * The circle of a close friend of the caller's: handles and names, never closeness, and empty
     * when the caller is not close enough to read it.
     */
    Flux<Friend> friendsOf(String userId);
}
