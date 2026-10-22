package com.calendar.mcp.application.mcp;

import com.calendar.mcp.application.dtos.EventSummaryResponse;
import com.calendar.mcp.application.mappers.EventSummaryToolMapperImpl;
import com.calendar.mcp.application.mappers.FriendToolMapperImpl;
import com.calendar.mcp.domain.models.Attendee;
import com.calendar.mcp.domain.models.Friend;
import com.calendar.mcp.domain.models.NewEvent;
import com.calendar.mcp.domain.models.Attendance;
import com.calendar.mcp.domain.models.EventSummary;
import com.calendar.mcp.domain.ports.EventCatalog;
import com.calendar.mcp.domain.ports.EventWriter;
import com.calendar.mcp.domain.ports.SocialGraph;
import com.calendar.mcp.domain.ports.UserDirectory;
import com.calendar.mcp.domain.services.EventAttendanceService;
import com.calendar.mcp.domain.services.EventCreationService;
import com.calendar.mcp.domain.services.EventInvitationService;
import com.calendar.mcp.domain.services.FriendsService;
import com.calendar.mcp.domain.services.MyEventsService;
import com.calendar.mcp.exception.BusinessErrorCode;
import com.calendar.mcp.exception.BusinessException;
import com.calendar.mcp.exception.TechnicalErrorCode;
import com.calendar.mcp.exception.TechnicalException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CalendarToolsTest {

    private static final String FROM = "2026-10-21T00:00:00Z";
    private static final String TO = "2026-10-22T00:00:00Z";

    /** Camille is the only friend these tests know by name. */
    private static final UserDirectory NAMES = userId ->
            "f1".equals(userId) ? Mono.just("Camille Durand") : Mono.empty();

    private CalendarTools tools;

    private record ReadOnlyCatalog(Flux<EventSummary> events) implements EventCatalog, EventWriter {
        @Override
        public Flux<EventSummary> subscribedEvents(Instant from, Instant to) {
            return events;
        }

        @Override
        public Mono<EventSummary> create(NewEvent event) {
            return Mono.error(new AssertionError("listing must not create anything"));
        }

        @Override
        public Mono<Void> invite(String eventId, String userId) {
            return Mono.empty();
        }

        @Override
        public Mono<Attendance> attendanceOf(String eventId) {
            return Mono.just(new Attendance(true, List.of()));
        }

        @Override
        public Mono<Void> join(String eventId) {
            return Mono.error(new AssertionError("this test does not join anything"));
        }

        @Override
        public Mono<Void> answerRequest(String eventId, String userId, boolean accept) {
            return Mono.error(new AssertionError("this test answers no request"));
        }
    }

    /** Records the range it was asked for. */
    private record RecordingCatalog(AtomicReference<Instant> asked) implements EventCatalog, EventWriter {
        @Override
        public Flux<EventSummary> subscribedEvents(Instant from, Instant to) {
            asked.set(from);
            return Flux.empty();
        }

        @Override
        public Mono<EventSummary> create(NewEvent event) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> invite(String eventId, String userId) {
            return Mono.empty();
        }

        @Override
        public Mono<Attendance> attendanceOf(String eventId) {
            return Mono.just(new Attendance(false, List.of()));
        }

        @Override
        public Mono<Void> join(String eventId) {
            return Mono.error(new AssertionError("this test does not join anything"));
        }

        @Override
        public Mono<Void> answerRequest(String eventId, String userId, boolean accept) {
            return Mono.error(new AssertionError("this test answers no request"));
        }
    }

    /** Says whether it was read at all. */
    private record MarkingCatalog(AtomicReference<Boolean> read) implements EventCatalog, EventWriter {
        @Override
        public Flux<EventSummary> subscribedEvents(Instant from, Instant to) {
            read.set(true);
            return Flux.empty();
        }

        @Override
        public Mono<EventSummary> create(NewEvent event) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> invite(String eventId, String userId) {
            return Mono.empty();
        }

        @Override
        public Mono<Attendance> attendanceOf(String eventId) {
            return Mono.just(new Attendance(false, List.of()));
        }

        @Override
        public Mono<Void> join(String eventId) {
            return Mono.error(new AssertionError("this test does not join anything"));
        }

        @Override
        public Mono<Void> answerRequest(String eventId, String userId, boolean accept) {
            return Mono.error(new AssertionError("this test answers no request"));
        }
    }

    /** Keeps what it was asked to create and answers it back, as the calendar would. */
    private record WritingCatalog(AtomicReference<NewEvent> created) implements EventCatalog, EventWriter {
        @Override
        public Flux<EventSummary> subscribedEvents(Instant from, Instant to) {
            return Flux.empty();
        }

        @Override
        public Mono<EventSummary> create(NewEvent event) {
            created.set(event);
            return Mono.just(new EventSummary("new-id", event.title(), event.startDate(), event.endDate(),
                    event.location(), event.allDay(), List.of(), 0));
        }

        @Override
        public Mono<Void> invite(String eventId, String userId) {
            return Mono.empty();
        }

        @Override
        public Mono<Attendance> attendanceOf(String eventId) {
            return Mono.just(new Attendance(false, List.of()));
        }

        @Override
        public Mono<Void> join(String eventId) {
            return Mono.error(new AssertionError("this test does not join anything"));
        }

        @Override
        public Mono<Void> answerRequest(String eventId, String userId, boolean accept) {
            return Mono.error(new AssertionError("this test answers no request"));
        }
    }

    /** Knows the caller's friends, and names anyone it was given. */
    private record Circle(Flux<Friend> mine) implements SocialGraph {
        @Override
        public Flux<Friend> friends() {
            return mine;
        }

        @Override
        public Mono<Friend> person(String userId) {
            return Mono.empty();
        }
    }

    /** Camille is this test's only friend, and the only handle invitations can resolve. */
    private static final SocialGraph ONE_FRIEND =
            new Circle(Flux.just(new Friend("f1", "camille", 1234, null, "CLOSE")));

    private static <T extends EventCatalog & EventWriter> CalendarTools tools(T catalog) {
        return tools(catalog, ONE_FRIEND);
    }

    private static <T extends EventCatalog & EventWriter> CalendarTools tools(T catalog, SocialGraph graph) {
        FriendsService friends = new FriendsService(graph, NAMES);
        return new CalendarTools(new MyEventsService(catalog, NAMES), new EventCreationService(catalog), friends,
                new EventInvitationService(catalog, friends), new EventAttendanceService(catalog, catalog, graph),
                new EventSummaryToolMapperImpl(), new FriendToolMapperImpl());
    }

    private static CalendarTools toolsCreatingInto(AtomicReference<NewEvent> created) {
        return tools(new WritingCatalog(created));
    }

    private static CalendarTools toolsReturning(EventSummary... events) {
        return tools(new ReadOnlyCatalog(Flux.just(events)));
    }

    @BeforeEach
    void setUp() {
        tools = toolsReturning(new EventSummary("e1", "Dinner",
                Instant.parse("2026-10-21T19:00:00Z"), Instant.parse("2026-10-21T22:00:00Z"),
                "Niort", false, List.of(Attendee.unnamed("f1", "CLOSE")), 3));
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
        RecordingCatalog recording = new RecordingCatalog(asked);
        CalendarTools recordingTools =
                tools(recording);

        StepVerifier.create(recordingTools.listMyEvents(FROM, TO))
                .expectNextCount(1)
                .expectComplete().verify(Duration.ofSeconds(5));

        assertThat(asked.get()).isEqualTo(Instant.parse(FROM));
    }

    @Test
    void listMyEvents_shouldNameTheFriendsGoing() {
        StepVerifier.create(tools.listMyEvents(FROM, TO))
                .assertNext(events -> assertThat(events.getFirst().friendsGoing())
                        .singleElement()
                        .satisfies(friend -> {
                            assertThat(friend.name()).isEqualTo("Camille Durand");
                            assertThat(friend.closeness()).isEqualTo("CLOSE");
                        }))
                .expectComplete().verify(Duration.ofSeconds(5));
    }

    @Test
    void listMyEvents_shouldKeepTheUncappedCountEvenWhenTheListIsShorter() {
        StepVerifier.create(tools.listMyEvents(FROM, TO))
                .assertNext(events -> assertThat(events.getFirst().friendsGoingCount()).isEqualTo(3))
                .expectComplete().verify(Duration.ofSeconds(5));
    }

    @Test
    void listMyEvents_shouldLeaveOutAFriendItCouldNotName() {
        CalendarTools unnamed = toolsReturning(new EventSummary("e2", "Padel",
                Instant.parse("2026-10-21T10:00:00Z"), null, "Niort", false,
                List.of(Attendee.unnamed("unknown-id", "FRIEND")), 1));

        StepVerifier.create(unnamed.listMyEvents(FROM, TO))
                .assertNext(events -> {
                    assertThat(events.getFirst().friendsGoing()).isEmpty();
                    assertThat(events.getFirst().friendsGoingCount()).isEqualTo(1);
                })
                .expectComplete().verify(Duration.ofSeconds(5));
    }

    @Test
    void joinEvent_shouldSayWhatHappenedInWords() {
        StepVerifier.create(tools(new ReadOnlyCatalog(Flux.empty())).joinEvent("e1"))
                .expectNext("already taking part")
                .expectComplete().verify(Duration.ofSeconds(5));
    }

    @Test
    void listJoinRequests_shouldAnswerNobodyWhenNoneAsked() {
        StepVerifier.create(tools(new ReadOnlyCatalog(Flux.empty())).listJoinRequests("e1"))
                .assertNext(requests -> assertThat(requests).isEmpty())
                .expectComplete().verify(Duration.ofSeconds(5));
    }

    @Test
    void answerJoinRequest_shouldReportAHandleNobodyIsWaitingUnder() {
        StepVerifier.create(tools(new ReadOnlyCatalog(Flux.empty())).answerJoinRequest("e1", "zoe#1", true))
                .assertNext(outcome -> {
                    assertThat(outcome.invited()).isFalse();
                    assertThat(outcome.reason()).isEqualTo("nobody is waiting on this event with that handle");
                })
                .expectComplete().verify(Duration.ofSeconds(5));
    }

    @Test
    void answerJoinRequest_shouldTreatAMissingDecisionAsADecline() {
        StepVerifier.create(tools(new ReadOnlyCatalog(Flux.empty())).answerJoinRequest("e1", "zoe#1", null))
                .expectNextCount(1)
                .expectComplete().verify(Duration.ofSeconds(5));
    }

    @Test
    void listMyFriends_shouldAnswerTheFriendsWithTheirHandle() {
        StepVerifier.create(tools(new ReadOnlyCatalog(Flux.empty())).listMyFriends())
                .assertNext(friends -> assertThat(friends).singleElement().satisfies(friend -> {
                    assertThat(friend.handle()).isEqualTo("camille#1234");
                    assertThat(friend.closeness()).isEqualTo("CLOSE");
                }))
                .expectComplete().verify(Duration.ofSeconds(5));
    }

    @Test
    void listMyFriends_shouldNameAFriendTheDirectoryKnows() {
        StepVerifier.create(tools(new ReadOnlyCatalog(Flux.empty())).listMyFriends())
                .assertNext(friends -> assertThat(friends.getFirst().name()).isEqualTo("Camille Durand"))
                .expectComplete().verify(Duration.ofSeconds(5));
    }

    @Test
    void listMyFriends_shouldFallBackToTheHandleAsAName() {
        SocialGraph unnamed = new Circle(Flux.just(new Friend("unknown", "zoe", 42, null, "FRIEND")));

        StepVerifier.create(tools(new ReadOnlyCatalog(Flux.empty()), unnamed).listMyFriends())
                .assertNext(friends -> assertThat(friends.getFirst().name()).isEqualTo("zoe#42"))
                .expectComplete().verify(Duration.ofSeconds(5));
    }

    @Test
    void listMyFriends_shouldAnswerAnEmptyListForSomeoneWithNoFriends() {
        StepVerifier.create(tools(new ReadOnlyCatalog(Flux.empty()), new Circle(Flux.empty())).listMyFriends())
                .assertNext(friends -> assertThat(friends).isEmpty())
                .expectComplete().verify(Duration.ofSeconds(5));
    }

    @Test
    void inviteToEvent_shouldInviteAFriendByHandle() {
        StepVerifier.create(tools(new ReadOnlyCatalog(Flux.empty())).inviteToEvent("e1", List.of("camille#1234")))
                .assertNext(outcomes -> assertThat(outcomes).singleElement().satisfies(outcome -> {
                    assertThat(outcome.friend()).isEqualTo("camille#1234");
                    assertThat(outcome.invited()).isTrue();
                }))
                .expectComplete().verify(Duration.ofSeconds(5));
    }

    @Test
    void inviteToEvent_shouldReportSomeoneWhoIsNotAFriendWithoutFailing() {
        StepVerifier.create(tools(new ReadOnlyCatalog(Flux.empty())).inviteToEvent("e1", List.of("stranger#9")))
                .assertNext(outcomes -> {
                    assertThat(outcomes.getFirst().invited()).isFalse();
                    assertThat(outcomes.getFirst().reason()).isEqualTo("not among your friends");
                })
                .expectComplete().verify(Duration.ofSeconds(5));
    }

    @Test
    void inviteToEvent_shouldRejectAMissingEventId() {
        StepVerifier.create(tools(new ReadOnlyCatalog(Flux.empty())).inviteToEvent(null, List.of("camille#1234")))
                .verifyErrorSatisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(BusinessErrorCode.EVENT_ID_REQUIRED));
    }

    @Test
    void inviteToEvent_shouldRejectAnEmptyGuestList() {
        StepVerifier.create(tools(new ReadOnlyCatalog(Flux.empty())).inviteToEvent("e1", List.of()))
                .verifyErrorSatisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(BusinessErrorCode.NOBODY_TO_INVITE));
    }

    @Test
    void createEvent_shouldCreateTheEventAndAnswerItBack() {
        AtomicReference<NewEvent> created = new AtomicReference<>();
        CalendarTools writing = toolsCreatingInto(created);

        StepVerifier.create(writing.createEvent("Dîner", "2026-10-24T18:30:00Z", "2026-10-24T22:00:00Z",
                        "Niort", false, "chez Paul"))
                .assertNext(event -> assertThat(event.title()).isEqualTo("Dîner"))
                .expectComplete().verify(Duration.ofSeconds(5));

        assertThat(created.get().location()).isEqualTo("Niort");
        assertThat(created.get().description()).isEqualTo("chez Paul");
        assertThat(created.get().allDay()).isFalse();
    }

    @Test
    void createEvent_shouldAcceptNoEndAndNoDescription() {
        AtomicReference<NewEvent> created = new AtomicReference<>();

        StepVerifier.create(toolsCreatingInto(created)
                        .createEvent("Badminton", "2026-10-24T18:30:00Z", null, "Chauray", false, null))
                .expectNextCount(1)
                .expectComplete().verify(Duration.ofSeconds(5));

        assertThat(created.get().endDate()).isNull();
        assertThat(created.get().description()).isNull();
    }

    @Test
    void createEvent_shouldSnapAnAllDayEventToWholeDays() {
        AtomicReference<NewEvent> created = new AtomicReference<>();

        StepVerifier.create(toolsCreatingInto(created)
                        .createEvent("Vacances", "2026-10-24T18:30:00Z", null, "Biarritz", true, null))
                .expectNextCount(1)
                .expectComplete().verify(Duration.ofSeconds(5));

        assertThat(created.get().startDate()).isEqualTo(Instant.parse("2026-10-24T00:00:00Z"));
        assertThat(created.get().endDate()).isEqualTo(Instant.parse("2026-10-25T00:00:00Z"));
    }

    @Test
    void createEvent_shouldTreatAMissingAllDayAsATimedEvent() {
        AtomicReference<NewEvent> created = new AtomicReference<>();

        StepVerifier.create(toolsCreatingInto(created)
                        .createEvent("Dîner", "2026-10-24T18:30:00Z", null, "Niort", null, null))
                .expectNextCount(1)
                .expectComplete().verify(Duration.ofSeconds(5));

        assertThat(created.get().allDay()).isFalse();
    }

    @Test
    void createEvent_shouldRejectAnUnreadableStart() {
        StepVerifier.create(toolsCreatingInto(new AtomicReference<>())
                        .createEvent("Dîner", "vendredi soir", null, "Niort", false, null))
                .verifyErrorSatisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(BusinessErrorCode.TIME_RANGE_UNREADABLE));
    }

    @Test
    void createEvent_shouldRejectAnUnreadableEnd() {
        StepVerifier.create(toolsCreatingInto(new AtomicReference<>())
                        .createEvent("Dîner", "2026-10-24T18:30:00Z", "22h", "Niort", false, null))
                .verifyError(BusinessException.class);
    }

    @Test
    void createEvent_shouldRejectAMissingPlace() {
        StepVerifier.create(toolsCreatingInto(new AtomicReference<>())
                        .createEvent("Dîner", "2026-10-24T18:30:00Z", null, "  ", false, null))
                .verifyErrorSatisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(BusinessErrorCode.LOCATION_REQUIRED));
    }

    @Test
    void createEvent_shouldNotCreateAnythingWhileTheMonoIsNotSubscribed() {
        AtomicReference<NewEvent> created = new AtomicReference<>();

        toolsCreatingInto(created).createEvent("Dîner", "2026-10-24T18:30:00Z", null, "Niort", false, null);

        assertThat(created.get()).isNull();
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
        ReadOnlyCatalog failing =
                new ReadOnlyCatalog(Flux.error(new TechnicalException(TechnicalErrorCode.GATEWAY_REFUSED)));
        CalendarTools failingTools = tools(failing);

        StepVerifier.create(failingTools.listMyEvents(FROM, TO))
                .verifyErrorSatisfies(error -> assertThat(((TechnicalException) error).getErrorCode())
                        .isEqualTo(TechnicalErrorCode.GATEWAY_REFUSED));
    }

    @Test
    void listMyEvents_shouldNotBlockTheCallingThread() {
        // The tool is a Mono the server subscribes: building it must do no work at all.
        AtomicReference<Boolean> read = new AtomicReference<>(false);
        MarkingCatalog catalog = new MarkingCatalog(read);
        CalendarTools lazyTools = tools(catalog);

        lazyTools.listMyEvents(FROM, TO);

        assertThat(read.get()).isFalse();
    }
}
