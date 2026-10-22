package com.calendar.mcp.domain.services;

import com.calendar.mcp.domain.models.Attendance;
import com.calendar.mcp.domain.models.EventSummary;
import com.calendar.mcp.domain.models.Friend;
import com.calendar.mcp.domain.models.JoinOutcome;
import com.calendar.mcp.domain.models.NewEvent;
import com.calendar.mcp.domain.ports.EventCatalog;
import com.calendar.mcp.domain.ports.EventWriter;
import com.calendar.mcp.domain.ports.SocialGraph;
import com.calendar.mcp.exception.BusinessErrorCode;
import com.calendar.mcp.exception.BusinessException;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class EventAttendanceServiceTest {

    /** Answers each attendance in turn, so before and after a join can differ. */
    private record Standing(Deque<Attendance> answers, AtomicInteger joins,
                            AtomicReference<String> answered, boolean failAnswer) implements EventCatalog, EventWriter {
        @Override
        public Flux<EventSummary> subscribedEvents(Instant from, Instant to) {
            return Flux.error(new AssertionError("joining must not read the calendar"));
        }

        @Override
        public Mono<EventSummary> create(NewEvent event) {
            return Mono.error(new AssertionError("joining must not create anything"));
        }

        @Override
        public Mono<Void> invite(String eventId, String userId) {
            return Mono.error(new AssertionError("joining must not invite anyone"));
        }

        @Override
        public Mono<Attendance> attendanceOf(String eventId) {
            return Mono.just(answers.size() > 1 ? answers.poll() : answers.peek());
        }

        @Override
        public Mono<Void> join(String eventId) {
            joins.incrementAndGet();
            return Mono.empty();
        }

        @Override
        public Mono<Void> answerRequest(String eventId, String userId, boolean accept) {
            if (failAnswer) {
                return Mono.error(new IllegalStateException("refused"));
            }
            answered.set(userId + ":" + accept);
            return Mono.empty();
        }
    }

    private record Directory(Friend known) implements SocialGraph {
        @Override
        public Flux<Friend> friends() {
            return Flux.error(new AssertionError("answering must not read the caller's friends"));
        }

        @Override
        public Mono<Friend> person(String userId) {
            return known != null && known.userId().equals(userId) ? Mono.just(known) : Mono.empty();
        }
    }

    private static final Friend ASKER = new Friend("u9", "emma", 1665, null, null);

    /** The same double on both faces, as the one adapter is in production. */
    private static EventAttendanceService service(Standing catalog, Directory directory) {
        return new EventAttendanceService(catalog, catalog, directory);
    }

    private static Standing standing(Attendance... answers) {
        return new Standing(new ArrayDeque<>(List.of(answers)), new AtomicInteger(), new AtomicReference<>(), false);
    }

    @Test
    void join_shouldJoinWhenTheCalendarTakesTheCallerIn() {
        Standing catalog = standing(new Attendance(false, List.of()), new Attendance(true, List.of()));

        StepVerifier.create(new EventAttendanceService(catalog, catalog, new Directory(null)).join("e1"))
                .expectNext(JoinOutcome.JOINED)
                .verifyComplete();

        assertThat(catalog.joins()).hasValue(1);
    }

    @Test
    void join_shouldSayItAskedWhenTheOrganizerHasToAnswer() {
        Standing catalog = standing(new Attendance(false, List.of()), new Attendance(false, List.of("me")));

        StepVerifier.create(new EventAttendanceService(catalog, catalog, new Directory(null)).join("e1"))
                .expectNext(JoinOutcome.REQUESTED)
                .verifyComplete();
    }

    @Test
    void join_shouldChangeNothingWhenAlreadyTakingPart() {
        Standing catalog = standing(new Attendance(true, List.of()));

        StepVerifier.create(new EventAttendanceService(catalog, catalog, new Directory(null)).join("e1"))
                .expectNext(JoinOutcome.ALREADY_TAKING_PART)
                .verifyComplete();

        assertThat(catalog.joins()).hasValue(0);
    }

    @Test
    void join_shouldNotAskTwice() {
        Standing catalog = standing(new Attendance(false, List.of("me")));

        StepVerifier.create(new EventAttendanceService(catalog, catalog, new Directory(null)).join("e1"))
                .expectNext(JoinOutcome.ALREADY_REQUESTED)
                .verifyComplete();

        assertThat(catalog.joins()).hasValue(0);
    }

    @Test
    void join_shouldRejectAMissingEventId() {
        StepVerifier.create(service(standing(new Attendance(false, List.of())), new Directory(null)).join(" "))
                .verifyErrorSatisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(BusinessErrorCode.EVENT_ID_REQUIRED));
    }

    @Test
    void pendingRequests_shouldNameWhoIsWaiting() {
        Standing catalog = standing(new Attendance(true, List.of("u9")));

        StepVerifier.create(new EventAttendanceService(catalog, catalog, new Directory(ASKER)).pendingRequests("e1")
                        .map(Friend::handle))
                .expectNext("emma#1665")
                .verifyComplete();
    }

    @Test
    void pendingRequests_shouldLeaveOutSomeoneTheDirectoryCannotName() {
        Standing catalog = standing(new Attendance(true, List.of("unknown")));

        StepVerifier.create(new EventAttendanceService(catalog, catalog, new Directory(ASKER)).pendingRequests("e1"))
                .verifyComplete();
    }

    @Test
    void pendingRequests_shouldBeEmptyWhenNobodyAsked() {
        StepVerifier.create(service(standing(new Attendance(true, List.of())), new Directory(ASKER)).pendingRequests("e1"))
                .verifyComplete();
    }

    @Test
    void answer_shouldAcceptTheAskerThatHandleNames() {
        Standing catalog = standing(new Attendance(true, List.of("u9")));

        StepVerifier.create(new EventAttendanceService(catalog, catalog, new Directory(ASKER)).answer("e1", "emma#1665", true))
                .assertNext(outcome -> {
                    assertThat(outcome.sent()).isTrue();
                    assertThat(outcome.handle()).isEqualTo("emma#1665");
                })
                .verifyComplete();

        assertThat(catalog.answered()).hasValue("u9:true");
    }

    @Test
    void answer_shouldDeclineWhenToldTo() {
        Standing catalog = standing(new Attendance(true, List.of("u9")));

        StepVerifier.create(new EventAttendanceService(catalog, catalog, new Directory(ASKER)).answer("e1", "emma", false))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(catalog.answered()).hasValue("u9:false");
    }

    @Test
    void answer_shouldRefuseAHandleNobodyIsWaitingUnder() {
        Standing catalog = standing(new Attendance(true, List.of("u9")));

        StepVerifier.create(new EventAttendanceService(catalog, catalog, new Directory(ASKER)).answer("e1", "zoe#1", true))
                .assertNext(outcome -> {
                    assertThat(outcome.sent()).isFalse();
                    assertThat(outcome.reason()).isEqualTo("nobody is waiting on this event with that handle");
                })
                .verifyComplete();

        assertThat(catalog.answered()).hasValue(null);
    }

    @Test
    void answer_shouldReportACalendarRefusalWithoutFailing() {
        Standing catalog = new Standing(new ArrayDeque<>(List.of(new Attendance(true, List.of("u9")))),
                new AtomicInteger(), new AtomicReference<>(), true);

        StepVerifier.create(new EventAttendanceService(catalog, catalog, new Directory(ASKER)).answer("e1", "emma#1665", true))
                .assertNext(outcome -> assertThat(outcome.reason()).isEqualTo("the calendar refused the answer"))
                .verifyComplete();
    }

    @Test
    void answer_shouldRejectAMissingHandle() {
        StepVerifier.create(service(standing(new Attendance(true, List.of())), new Directory(ASKER)).answer("e1", "  ", true))
                .verifyErrorSatisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(BusinessErrorCode.NOBODY_TO_INVITE));
    }
}
