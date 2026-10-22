package com.calendar.mcp.domain.services;

import com.calendar.mcp.domain.models.EventSummary;
import com.calendar.mcp.domain.models.GuestPolicy;
import com.calendar.mcp.domain.models.NewEvent;
import com.calendar.mcp.domain.ports.EventCatalog;
import com.calendar.mcp.exception.BusinessErrorCode;
import com.calendar.mcp.exception.BusinessException;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class EventCreationServiceTest {

    private static final Instant START = Instant.parse("2026-10-24T18:30:00Z");

    /** Keeps whatever it was asked to create, and answers it back as the calendar would. */
    private record CapturingCatalog(AtomicReference<NewEvent> created) implements EventCatalog {
        @Override
        public Flux<EventSummary> subscribedEvents(Instant from, Instant to) {
            return Flux.error(new AssertionError("creating must not read the calendar"));
        }

        @Override
        public Mono<EventSummary> create(NewEvent event) {
            created.set(event);
            return Mono.just(new EventSummary("new-id", event.title(), event.startDate(), event.endDate(),
                    event.location(), event.allDay(), List.of(), 0));
        }

        @Override
        public Mono<Void> invite(String eventId, String userId) {
            return Mono.error(new AssertionError("this test does not invite anyone"));
        }

        @Override
        public Mono<GuestPolicy> guestPolicyOf(String eventId) {
            return Mono.just(GuestPolicy.organizersFriends());
        }
    }

    private static NewEvent dinner(String title, String location) {
        return new NewEvent(title, START, Instant.parse("2026-10-24T22:00:00Z"), location, "chez Paul", false);
    }

    @Test
    void create_shouldHandTheEventToTheCalendarAndAnswerItBack() {
        AtomicReference<NewEvent> created = new AtomicReference<>();
        EventCreationService service = new EventCreationService(new CapturingCatalog(created));

        StepVerifier.create(service.create(dinner("Dîner", "Niort")))
                .assertNext(event -> {
                    assertThat(event.id()).isEqualTo("new-id");
                    assertThat(event.title()).isEqualTo("Dîner");
                })
                .verifyComplete();

        assertThat(created.get().location()).isEqualTo("Niort");
    }

    @Test
    void create_shouldNormaliseAnAllDayEventBeforeSendingIt() {
        AtomicReference<NewEvent> created = new AtomicReference<>();
        EventCreationService service = new EventCreationService(new CapturingCatalog(created));
        NewEvent allDay = new NewEvent("Vacances", START, null, "Biarritz", null, true);

        StepVerifier.create(service.create(allDay)).expectNextCount(1).verifyComplete();

        assertThat(created.get().startDate()).isEqualTo(Instant.parse("2026-10-24T00:00:00Z"));
        assertThat(created.get().endDate()).isEqualTo(Instant.parse("2026-10-25T00:00:00Z"));
    }

    @Test
    void create_shouldRejectAMissingTitle() {
        StepVerifier.create(new EventCreationService(new CapturingCatalog(new AtomicReference<>()))
                        .create(dinner("   ", "Niort")))
                .verifyErrorSatisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(BusinessErrorCode.TITLE_REQUIRED));
    }

    @Test
    void create_shouldRejectAMissingLocation() {
        StepVerifier.create(new EventCreationService(new CapturingCatalog(new AtomicReference<>()))
                        .create(dinner("Dîner", null)))
                .verifyErrorSatisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(BusinessErrorCode.LOCATION_REQUIRED));
    }

    @Test
    void create_shouldRejectAnEndBeforeTheStart() {
        NewEvent backwards = new NewEvent("Dîner", START, Instant.parse("2026-10-24T17:00:00Z"),
                "Niort", null, false);

        StepVerifier.create(new EventCreationService(new CapturingCatalog(new AtomicReference<>())).create(backwards))
                .verifyErrorSatisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(BusinessErrorCode.END_BEFORE_START));
    }

    @Test
    void create_shouldRejectAMissingStart() {
        NewEvent undated = new NewEvent("Dîner", null, null, "Niort", null, false);

        StepVerifier.create(new EventCreationService(new CapturingCatalog(new AtomicReference<>())).create(undated))
                .verifyError(BusinessException.class);
    }

    @Test
    void create_shouldNotCallTheCalendarWhenTheRequestIsRefused() {
        AtomicReference<NewEvent> created = new AtomicReference<>();

        StepVerifier.create(new EventCreationService(new CapturingCatalog(created)).create(dinner(null, "Niort")))
                .verifyError(BusinessException.class);

        assertThat(created.get()).isNull();
    }

    @Test
    void create_shouldPropagateAFailureFromTheCalendar() {
        EventCatalog failing = new EventCatalog() {
            @Override
            public Flux<EventSummary> subscribedEvents(Instant from, Instant to) {
                return Flux.empty();
            }

            @Override
            public Mono<EventSummary> create(NewEvent event) {
                return Mono.error(new IllegalStateException("refused"));
            }

            @Override
            public Mono<Void> invite(String eventId, String userId) {
                return Mono.error(new AssertionError("this test does not invite anyone"));
            }

            @Override
            public Mono<GuestPolicy> guestPolicyOf(String eventId) {
                return Mono.just(GuestPolicy.organizersFriends());
            }
        };

        StepVerifier.create(new EventCreationService(failing).create(dinner("Dîner", "Niort")))
                .verifyError(IllegalStateException.class);
    }
}
