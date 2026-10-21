package com.calendar.mcp.domain.services;

import com.calendar.mcp.domain.models.Attendee;
import com.calendar.mcp.domain.models.EventSummary;
import com.calendar.mcp.domain.ports.EventCatalog;
import com.calendar.mcp.domain.ports.UserDirectory;
import com.calendar.mcp.exception.BusinessErrorCode;
import com.calendar.mcp.exception.BusinessException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Answers "what do I have between these two dates", soonest first, saying who else goes. */
public class MyEventsService {

    /** A tool answer is read by a model: past a few dozen events it stops helping and only costs context. */
    static final int MAX_EVENTS = 50;

    /** Enough to keep the directory lookups quick without hammering wely-users. */
    static final int NAME_LOOKUP_CONCURRENCY = 4;

    private final EventCatalog eventCatalog;
    private final UserDirectory userDirectory;

    public MyEventsService(EventCatalog eventCatalog, UserDirectory userDirectory) {
        this.eventCatalog = eventCatalog;
        this.userDirectory = userDirectory;
    }

    /**
     * The caller's events overlapping [from, to), soonest first and capped at {@value #MAX_EVENTS}.
     * An event counts as overlapping as soon as it is not wholly before or wholly after the range.
     *
     * <p>The range goes to the catalog *and* is applied here. That is deliberate rather than
     * redundant: the rule belongs to the domain, and an API version that ignored the parameters
     * would otherwise widen this answer silently.
     */
    public Flux<EventSummary> myEvents(Instant from, Instant to) {
        if (from == null || to == null || !to.isAfter(from)) {
            return Flux.error(new BusinessException(BusinessErrorCode.TIME_RANGE_INVALID));
        }
        return eventCatalog.subscribedEvents(from, to)
                .filter(event -> overlaps(event, from, to))
                .sort(Comparator.comparing(EventSummary::startDate))
                .take(MAX_EVENTS)
                // Collected first so the same friend, going to four events, is looked up once --
                // and so no name is fetched for an event the cap above dropped.
                .collectList()
                .flatMapMany(this::withNames);
    }

    private Flux<EventSummary> withNames(List<EventSummary> events) {
        return Flux.fromIterable(events)
                .flatMapIterable(EventSummary::friendsGoing)
                .map(Attendee::userId)
                .distinct()
                .flatMap(this::nameOf, NAME_LOOKUP_CONCURRENCY)
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .flatMapMany(names -> Flux.fromIterable(events).map(event -> named(event, names)));
    }

    private Mono<Map.Entry<String, String>> nameOf(String userId) {
        return userDirectory.displayName(userId).map(name -> Map.entry(userId, name));
    }

    private EventSummary named(EventSummary event, Map<String, String> names) {
        if (event.friendsGoing().isEmpty()) {
            return event;
        }
        return event.withFriendsGoing(event.friendsGoing().stream()
                .map(attendee -> new Attendee(attendee.userId(), names.get(attendee.userId()), attendee.closeness()))
                .toList());
    }

    private boolean overlaps(EventSummary event, Instant from, Instant to) {
        if (event.startDate() == null) {
            return false;
        }
        return event.startDate().isBefore(to) && !event.effectiveEnd().isBefore(from);
    }
}
