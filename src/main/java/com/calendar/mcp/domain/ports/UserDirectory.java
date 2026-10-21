package com.calendar.mcp.domain.ports;

import reactor.core.publisher.Mono;

/** Puts a name on a user id, which the calendar never does itself. */
public interface UserDirectory {

    /**
     * The display name of that user, or an empty Mono when they cannot be named -- not an error:
     * a profile the caller may not read must not take the whole answer down with it.
     */
    Mono<String> displayName(String userId);
}
