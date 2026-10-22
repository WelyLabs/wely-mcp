package com.calendar.mcp.domain.ports;

import com.calendar.mcp.domain.models.Friend;
import reactor.core.publisher.Flux;

/** Who the caller is friends with. */
public interface SocialGraph {

    /** The caller's accepted friends, pending requests excluded. */
    Flux<Friend> friends();
}
