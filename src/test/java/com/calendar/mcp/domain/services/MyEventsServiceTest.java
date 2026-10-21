package com.calendar.mcp.domain.services;

import com.calendar.mcp.domain.models.EventSummary;
import com.calendar.mcp.domain.ports.EventCatalog;
import com.calendar.mcp.exception.BusinessErrorCode;
import com.calendar.mcp.exception.BusinessException;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class MyEventsServiceTest {

    private static final Instant FROM = Instant.parse("2026-10-21T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-10-22T00:00:00Z");

    private static EventSummary event(String id, String start, String end) {
        return new EventSummary(id, "title-" + id,
                start == null ? null : Instant.parse(start),
                end == null ? null : Instant.parse(end),
                "somewhere", false);
    }

    private static MyEventsService serviceReturning(EventSummary... events) {
        EventCatalog catalog = (from, to) -> Flux.just(events);
        return new MyEventsService(catalog);
    }

    @Test
    void myEvents_shouldKeepOnlyEventsOverlappingTheRange() {
        MyEventsService service = serviceReturning(
                event("inside", "2026-10-21T10:00:00Z", "2026-10-21T12:00:00Z"),
                event("before", "2026-10-20T08:00:00Z", "2026-10-20T09:00:00Z"),
                event("after", "2026-10-23T08:00:00Z", "2026-10-23T09:00:00Z"));

        StepVerifier.create(service.myEvents(FROM, TO).map(EventSummary::id))
                .expectNext("inside")
                .verifyComplete();
    }

    @Test
    void myEvents_shouldKeepAnEventStraddlingTheRange() {
        MyEventsService service = serviceReturning(
                event("straddles", "2026-10-20T20:00:00Z", "2026-10-21T04:00:00Z"));

        StepVerifier.create(service.myEvents(FROM, TO).map(EventSummary::id))
                .expectNext("straddles")
                .verifyComplete();
    }

    @Test
    void myEvents_shouldTreatTheRangeEndAsExclusive() {
        MyEventsService service = serviceReturning(event("onTheEdge", "2026-10-22T00:00:00Z", null));

        StepVerifier.create(service.myEvents(FROM, TO))
                .verifyComplete();
    }

    @Test
    void myEvents_shouldKeepAnEventWithoutEndWhenItStartsInTheRange() {
        MyEventsService service = serviceReturning(event("open", "2026-10-21T23:00:00Z", null));

        StepVerifier.create(service.myEvents(FROM, TO).map(EventSummary::id))
                .expectNext("open")
                .verifyComplete();
    }

    @Test
    void myEvents_shouldReturnTheSoonestFirst() {
        MyEventsService service = serviceReturning(
                event("later", "2026-10-21T18:00:00Z", null),
                event("sooner", "2026-10-21T09:00:00Z", null));

        StepVerifier.create(service.myEvents(FROM, TO).map(EventSummary::id))
                .expectNext("sooner", "later")
                .verifyComplete();
    }

    @Test
    void myEvents_shouldCapWhatItReturns() {
        EventSummary[] many = IntStream.range(0, MyEventsService.MAX_EVENTS + 10)
                .mapToObj(index -> event("e" + index, "2026-10-21T10:00:00Z", null))
                .toArray(EventSummary[]::new);

        StepVerifier.create(serviceReturning(many).myEvents(FROM, TO))
                .expectNextCount(MyEventsService.MAX_EVENTS)
                .verifyComplete();
    }

    @Test
    void myEvents_shouldIgnoreAnEventWithoutStart() {
        MyEventsService service = serviceReturning(event("undated", null, null));

        StepVerifier.create(service.myEvents(FROM, TO))
                .verifyComplete();
    }

    @Test
    void myEvents_shouldRejectWhenTheEndIsNotAfterTheStart() {
        StepVerifier.create(serviceReturning().myEvents(TO, FROM))
                .verifyErrorSatisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(BusinessErrorCode.TIME_RANGE_INVALID));
    }

    @Test
    void myEvents_shouldRejectWhenABoundIsMissing() {
        StepVerifier.create(serviceReturning().myEvents(null, TO))
                .verifyErrorSatisfies(error -> assertThat(error).isInstanceOf(BusinessException.class));
    }

    @Test
    void myEvents_shouldNotQueryTheCatalogWhenTheRangeIsInvalid() {
        EventCatalog refusing = (from, to) -> Flux.error(new AssertionError("the catalog must not be read"));

        StepVerifier.create(new MyEventsService(refusing).myEvents(TO, FROM))
                .verifyError(BusinessException.class);
    }

    @Test
    void myEvents_shouldPropagateACatalogFailure() {
        EventCatalog failing = (from, to) -> Flux.error(new IllegalStateException("gateway down"));

        StepVerifier.create(new MyEventsService(failing).myEvents(FROM, TO))
                .verifyError(IllegalStateException.class);
    }

    @Test
    void myEvents_shouldCompleteOnAnEmptyCalendar() {
        StepVerifier.create(serviceReturning().myEvents(FROM, TO))
                .verifyComplete();
    }

    @Test
    void effectiveEnd_shouldFallBackToTheStartWhenThereIsNoEnd() {
        List<EventSummary> events = List.of(event("open", "2026-10-21T10:00:00Z", null));

        assertThat(events.getFirst().effectiveEnd()).isEqualTo(Instant.parse("2026-10-21T10:00:00Z"));
    }

    @Test
    void myEvents_shouldNotHangOnALargeCalendar() {
        EventSummary[] many = IntStream.range(0, 5_000)
                .mapToObj(index -> event("e" + index, "2026-10-21T10:00:00Z", null))
                .toArray(EventSummary[]::new);

        StepVerifier.create(serviceReturning(many).myEvents(FROM, TO))
                .expectNextCount(MyEventsService.MAX_EVENTS)
                .expectComplete()
                .verify(Duration.ofSeconds(5));
    }
}
