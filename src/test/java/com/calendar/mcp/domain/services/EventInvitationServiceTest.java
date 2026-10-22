package com.calendar.mcp.domain.services;

import com.calendar.mcp.domain.models.EventSummary;
import com.calendar.mcp.domain.models.GuestPolicy;
import com.calendar.mcp.domain.models.Friend;
import com.calendar.mcp.domain.models.Invitation;
import com.calendar.mcp.domain.models.NewEvent;
import com.calendar.mcp.domain.ports.EventCatalog;
import com.calendar.mcp.domain.ports.SocialGraph;
import com.calendar.mcp.domain.ports.UserDirectory;
import com.calendar.mcp.exception.BusinessErrorCode;
import com.calendar.mcp.exception.BusinessException;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EventInvitationServiceTest {

    /** A graph that knows the caller's friends and nobody else's circle. */
    private record OwnFriends(Flux<Friend> mine) implements SocialGraph {
        @Override
        public Flux<Friend> friends() {
            return mine;
        }

        @Override
        public Flux<Friend> friendsOf(String userId) {
            return Flux.empty();
        }
    }

    private static final SocialGraph TWO_FRIENDS = new OwnFriends(Flux.just(
            new Friend("u1", "camille", 1234, null, "CLOSE"),
            new Friend("u2", "hugo", 5678, null, "FRIEND")));

    /** Records who was invited, in order, and can be told to refuse one of them. */
    private record InvitingCatalog(List<String> invited, String refuses) implements EventCatalog {
        @Override
        public Flux<EventSummary> subscribedEvents(Instant from, Instant to) {
            return Flux.error(new AssertionError("inviting must not read the calendar"));
        }

        @Override
        public Mono<EventSummary> create(NewEvent event) {
            return Mono.error(new AssertionError("inviting must not create anything"));
        }

        @Override
        public Mono<Void> invite(String eventId, String userId) {
            if (userId.equals(refuses)) {
                return Mono.error(new IllegalStateException("already taking part"));
            }
            invited.add(userId);
            return Mono.empty();
        }

        @Override
        public Mono<GuestPolicy> guestPolicyOf(String eventId) {
            return Mono.just(GuestPolicy.organizersFriends());
        }
    }

    /** A surprise whose honoree is someone else, and which records who was invited. */
    private record SurpriseCatalog(String honoreeId, List<String> invited) implements EventCatalog {
        SurpriseCatalog(String honoreeId) {
            this(honoreeId, new ArrayList<>());
        }

        @Override
        public Flux<EventSummary> subscribedEvents(Instant from, Instant to) {
            return Flux.error(new AssertionError("inviting must not read the calendar"));
        }

        @Override
        public Mono<EventSummary> create(NewEvent event) {
            return Mono.error(new AssertionError("inviting must not create anything"));
        }

        @Override
        public Mono<Void> invite(String eventId, String userId) {
            invited.add(userId);
            return Mono.empty();
        }

        @Override
        public Mono<GuestPolicy> guestPolicyOf(String eventId) {
            return Mono.just(GuestPolicy.honoreesFriends(honoreeId));
        }
    }

    /** A graph that knows somebody else's circle, and nothing of the caller's own friends. */
    private record CircleOf(Flux<Friend> circle) implements SocialGraph {
        @Override
        public Flux<Friend> friends() {
            return Flux.error(new AssertionError("a surprise must not read the caller's own friends"));
        }

        @Override
        public Flux<Friend> friendsOf(String userId) {
            return circle;
        }
    }

    private static EventInvitationService serviceWith(InvitingCatalog catalog) {
        return new EventInvitationService(catalog, new FriendsService(TWO_FRIENDS, userId -> Mono.empty()), TWO_FRIENDS);
    }

    @Test
    void invite_shouldInviteEveryFriendNamed() {
        List<String> invited = new ArrayList<>();

        StepVerifier.create(serviceWith(new InvitingCatalog(invited, null))
                        .invite("e1", List.of("camille#1234", "hugo#5678")))
                .assertNext(outcomes -> assertThat(outcomes).allMatch(Invitation::sent))
                .verifyComplete();

        assertThat(invited).containsExactly("u1", "u2");
    }

    @Test
    void invite_shouldAnswerWithTheHandleItWasGiven() {
        StepVerifier.create(serviceWith(new InvitingCatalog(new ArrayList<>(), null))
                        .invite("e1", List.of("camille#1234")))
                .assertNext(outcomes -> assertThat(outcomes.getFirst().handle()).isEqualTo("camille#1234"))
                .verifyComplete();
    }

    @Test
    void invite_shouldRefuseSomeoneWhoIsNotAFriend() {
        List<String> invited = new ArrayList<>();

        StepVerifier.create(serviceWith(new InvitingCatalog(invited, null))
                        .invite("e1", List.of("stranger#1")))
                .assertNext(outcomes -> assertThat(outcomes).singleElement().satisfies(outcome -> {
                    assertThat(outcome.sent()).isFalse();
                    assertThat(outcome.reason()).isEqualTo("not among the people who may be invited to this event");
                }))
                .verifyComplete();

        assertThat(invited).isEmpty();
    }

    @Test
    void invite_shouldKeepInvitingAfterOneRefusal() {
        List<String> invited = new ArrayList<>();

        StepVerifier.create(serviceWith(new InvitingCatalog(invited, "u1"))
                        .invite("e1", List.of("camille#1234", "hugo#5678")))
                .assertNext(outcomes -> {
                    assertThat(outcomes.get(0).sent()).isFalse();
                    assertThat(outcomes.get(1).sent()).isTrue();
                })
                .verifyComplete();

        assertThat(invited).containsExactly("u2");
    }

    @Test
    void invite_shouldSayWhenTheCalendarRefused() {
        StepVerifier.create(serviceWith(new InvitingCatalog(new ArrayList<>(), "u1"))
                        .invite("e1", List.of("camille#1234")))
                .assertNext(outcomes -> assertThat(outcomes.getFirst().reason())
                        .isEqualTo("the calendar refused the invitation"))
                .verifyComplete();
    }

    @Test
    void invitableTo_shouldAnswerTheCallersFriends_onAStandardEvent() {
        StepVerifier.create(serviceWith(new InvitingCatalog(new ArrayList<>(), null)).invitableTo("e1")
                        .map(Friend::handle))
                .expectNext("camille#1234", "hugo#5678")
                .verifyComplete();
    }

    @Test
    void invitableTo_shouldAnswerTheHonoreesCircle_onASurprise() {
        Friend stranger = new Friend("u9", "emma", 1665, null, null);
        EventCatalog surprise = new SurpriseCatalog("u2");
        SocialGraph graph = new CircleOf(Flux.just(stranger));
        EventInvitationService service =
                new EventInvitationService(surprise, new FriendsService(graph, userId -> Mono.empty()), graph);

        StepVerifier.create(service.invitableTo("e1").map(Friend::handle))
                .expectNext("emma#1665")
                .verifyComplete();
    }

    @Test
    void invitableTo_shouldInviteFromTheHonoreesCircle_onASurprise() {
        List<String> invited = new ArrayList<>();
        Friend stranger = new Friend("u9", "emma", 1665, null, null);
        EventCatalog surprise = new SurpriseCatalog("u2", invited);
        SocialGraph graph = new CircleOf(Flux.just(stranger));
        EventInvitationService service =
                new EventInvitationService(surprise, new FriendsService(graph, userId -> Mono.empty()), graph);

        StepVerifier.create(service.invite("e1", List.of("emma#1665")))
                .assertNext(outcomes -> assertThat(outcomes.getFirst().sent()).isTrue())
                .verifyComplete();

        assertThat(invited).containsExactly("u9");
    }

    @Test
    void invite_shouldRefuseTheCallersOwnFriend_whenASurpriseHonoreeDoesNotKnowThem() {
        List<String> invited = new ArrayList<>();
        EventCatalog surprise = new SurpriseCatalog("u2", invited);
        SocialGraph graph = new CircleOf(Flux.empty());
        EventInvitationService service =
                new EventInvitationService(surprise, new FriendsService(graph, userId -> Mono.empty()), graph);

        StepVerifier.create(service.invite("e1", List.of("camille#1234")))
                .assertNext(outcomes -> {
                    assertThat(outcomes.getFirst().sent()).isFalse();
                    assertThat(outcomes.getFirst().reason()).isEqualTo("nobody can be invited to this event");
                })
                .verifyComplete();

        assertThat(invited).isEmpty();
    }

    @Test
    void invitableTo_shouldRejectAMissingEventId() {
        StepVerifier.create(serviceWith(new InvitingCatalog(new ArrayList<>(), null)).invitableTo(" "))
                .verifyErrorSatisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(BusinessErrorCode.EVENT_ID_REQUIRED));
    }

    @Test
    void invite_shouldRejectAMissingEventId() {
        StepVerifier.create(serviceWith(new InvitingCatalog(new ArrayList<>(), null))
                        .invite("  ", List.of("camille#1234")))
                .verifyErrorSatisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(BusinessErrorCode.EVENT_ID_REQUIRED));
    }

    @Test
    void invite_shouldRejectAnEmptyGuestList() {
        StepVerifier.create(serviceWith(new InvitingCatalog(new ArrayList<>(), null)).invite("e1", List.of()))
                .verifyErrorSatisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(BusinessErrorCode.NOBODY_TO_INVITE));
    }

    @Test
    void invite_shouldRejectNoGuestListAtAll() {
        StepVerifier.create(serviceWith(new InvitingCatalog(new ArrayList<>(), null)).invite("e1", null))
                .verifyError(BusinessException.class);
    }

    @Test
    void invite_shouldNotTouchTheCalendarWhenTheRequestIsRefused() {
        List<String> invited = new ArrayList<>();

        StepVerifier.create(serviceWith(new InvitingCatalog(invited, null)).invite(null, List.of("camille#1234")))
                .verifyError(BusinessException.class);

        assertThat(invited).isEmpty();
    }
}
