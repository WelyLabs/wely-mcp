package com.calendar.mcp.domain.services;

import com.calendar.mcp.domain.models.Friend;
import com.calendar.mcp.domain.ports.SocialGraph;
import com.calendar.mcp.domain.ports.UserDirectory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Comparator;

/** The caller's friends, closest first, each named. */
public class FriendsService {

    /** Same budget as the event listing: enough to be quick without hammering wely-users. */
    static final int NAME_LOOKUP_CONCURRENCY = 4;

    /** INTIMATE first, then CLOSE, then FRIEND, then anything unknown. */
    private static final Comparator<Friend> CLOSEST_FIRST =
            Comparator.<Friend>comparingInt(friend -> switch (friend.closeness() == null ? "" : friend.closeness()) {
                case "INTIMATE" -> 0;
                case "CLOSE" -> 1;
                case "FRIEND" -> 2;
                default -> 3;
            }).thenComparing(Friend::handle, Comparator.nullsLast(String::compareToIgnoreCase));

    private final SocialGraph socialGraph;
    private final UserDirectory userDirectory;

    public FriendsService(SocialGraph socialGraph, UserDirectory userDirectory) {
        this.socialGraph = socialGraph;
        this.userDirectory = userDirectory;
    }

    public Flux<Friend> friends() {
        return socialGraph.friends()
                .flatMap(this::named, NAME_LOOKUP_CONCURRENCY)
                .sort(CLOSEST_FIRST);
    }

    /**
     * The friend that handle names, or an empty Mono. Resolving against the caller's own friends is
     * what makes an invitation safe: a model cannot name someone they are not friends with.
     */
    public Mono<Friend> byHandle(String handle) {
        if (handle == null || handle.isBlank()) {
            return Mono.empty();
        }
        String wanted = handle.trim();
        return socialGraph.friends()
                .filter(friend -> matches(friend, wanted))
                .next();
    }

    private boolean matches(Friend friend, String wanted) {
        return wanted.equalsIgnoreCase(friend.handle()) || wanted.equalsIgnoreCase(friend.userName());
    }

    /** A friend whose profile cannot be read keeps their handle, which is enough to speak of them. */
    private Mono<Friend> named(Friend friend) {
        return userDirectory.displayName(friend.userId())
                .map(friend::named)
                .defaultIfEmpty(friend);
    }
}
