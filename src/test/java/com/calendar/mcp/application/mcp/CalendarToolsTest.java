package com.calendar.mcp.application.mcp;

import com.calendar.mcp.application.dtos.EventSummaryResponse;
import com.calendar.mcp.application.mappers.EventSummaryToolMapperImpl;
import com.calendar.mcp.domain.models.EventSummary;
import com.calendar.mcp.domain.ports.EventCatalog;
import com.calendar.mcp.domain.services.MyEventsService;
import com.calendar.mcp.exception.BusinessErrorCode;
import com.calendar.mcp.exception.BusinessException;
import com.calendar.mcp.exception.TechnicalErrorCode;
import com.calendar.mcp.exception.TechnicalException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CalendarToolsTest {

    private static final String FROM = "2026-10-21T00:00:00Z";
    private static final String TO = "2026-10-22T00:00:00Z";

    private CalendarTools tools;

    private static CalendarTools toolsReturning(EventSummary... events) {
        EventCatalog catalog = (from, to) -> Flux.just(events);
        return new CalendarTools(new MyEventsService(catalog), new EventSummaryToolMapperImpl());
    }

    @BeforeEach
    void setUp() {
        tools = toolsReturning(new EventSummary("e1", "Dinner",
                Instant.parse("2026-10-21T19:00:00Z"), Instant.parse("2026-10-21T22:00:00Z"),
                "Niort", false));
    }

    @Test
    void listMyEvents_shouldReturnTheCallersEventsInTheRange() {
        StepVerifier.create(tools.listMyEvents(FROM, TO))
                .assertNext(events -> assertThat(events).singleElement().satisfies(event -> {
                    assertThat(event.id()).isEqualTo("e1");
                    assertThat(event.title()).isEqualTo("Dinner");
                    assertThat(event.location()).isEqualTo("Niort");
                }))
                .expectComplete().verify(Duration.ofSeconds(5));
    }

    @Test
    void listMyEvents_shouldRenderDatesAsIsoInstants() {
        StepVerifier.create(tools.listMyEvents(FROM, TO))
                .assertNext(events -> {
                    assertThat(events.getFirst().startDate()).isEqualTo("2026-10-21T19:00:00Z");
                    assertThat(events.getFirst().endDate()).isEqualTo("2026-10-21T22:00:00Z");
                })
                .expectComplete().verify(Duration.ofSeconds(5));
    }

    @Test
    void listMyEvents_shouldReturnAnEmptyListWhenNothingMatches() {
        StepVerifier.create(tools.listMyEvents("2026-12-01T00:00:00Z", "2026-12-02T00:00:00Z"))
                .assertNext(events -> assertThat(events).isEmpty())
                .expectComplete().verify(Duration.ofSeconds(5));
    }

    @Test
    void listMyEvents_shouldPassTheRangeToTheCatalog() {
        AtomicReference<Instant> asked = new AtomicReference<>();
        EventCatalog recording = (from, to) -> {
            asked.set(from);
            return Flux.empty();
        };
        CalendarTools recordingTools =
                new CalendarTools(new MyEventsService(recording), new EventSummaryToolMapperImpl());

        StepVerifier.create(recordingTools.listMyEvents(FROM, TO))
                .expectNextCount(1)
                .expectComplete().verify(Duration.ofSeconds(5));

        assertThat(asked.get()).isEqualTo(Instant.parse(FROM));
    }

    @Test
    void listMyEvents_shouldRejectAnUnreadableDate() {
        StepVerifier.create(tools.listMyEvents("21/10/2026", TO))
                .verifyErrorSatisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(BusinessErrorCode.TIME_RANGE_UNREADABLE));
    }

    @Test
    void listMyEvents_shouldRejectAMissingDate() {
        StepVerifier.create(tools.listMyEvents(null, TO))
                .verifyError(BusinessException.class);
    }

    @Test
    void listMyEvents_shouldRejectAnInvertedRange() {
        StepVerifier.create(tools.listMyEvents(TO, FROM))
                .verifyErrorSatisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(BusinessErrorCode.TIME_RANGE_INVALID));
    }

    @Test
    void listMyEvents_shouldPropagateACatalogFailure() {
        EventCatalog failing = (from, to) -> Flux.error(new TechnicalException(TechnicalErrorCode.GATEWAY_REFUSED));
        CalendarTools failingTools =
                new CalendarTools(new MyEventsService(failing), new EventSummaryToolMapperImpl());

        StepVerifier.create(failingTools.listMyEvents(FROM, TO))
                .verifyErrorSatisfies(error -> assertThat(((TechnicalException) error).getErrorCode())
                        .isEqualTo(TechnicalErrorCode.GATEWAY_REFUSED));
    }

    @Test
    void listMyEvents_shouldNotBlockTheCallingThread() {
        // The tool is a Mono the server subscribes: building it must do no work at all.
        AtomicReference<Boolean> read = new AtomicReference<>(false);
        EventCatalog catalog = (from, to) -> {
            read.set(true);
            return Flux.empty();
        };
        CalendarTools lazyTools =
                new CalendarTools(new MyEventsService(catalog), new EventSummaryToolMapperImpl());

        lazyTools.listMyEvents(FROM, TO);

        assertThat(read.get()).isFalse();
    }
}
