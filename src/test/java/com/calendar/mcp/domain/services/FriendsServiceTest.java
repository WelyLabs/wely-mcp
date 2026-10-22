package com.calendar.mcp.domain.services;

import com.calendar.mcp.domain.models.Friend;
import com.calendar.mcp.domain.ports.SocialGraph;
import com.calendar.mcp.domain.ports.UserDirectory;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FriendsServiceTest {

    private static final UserDirectory NO_NAMES = userId -> Mono.empty();

    private static Friend friend(String id, String userName, String closeness) {
        return new Friend(id, userName, 1000 + id.length(), null, closeness);
    }

    private static FriendsService serviceWith(UserDirectory directory, Friend... friends) {
        SocialGraph graph = () -> Flux.just(friends);
        return new FriendsService(graph, directory);
    }

    @Test
    void friends_shouldAnswerTheClosestFirst() {
        FriendsService service = serviceWith(NO_NAMES,
                friend("u1", "far", "FRIEND"),
                friend("u2", "closest", "INTIMATE"),
                friend("u3", "near", "CLOSE"));

        StepVerifier.create(service.friends().map(Friend::userName))
                .expectNext("closest", "near", "far")
                .verifyComplete();
    }

    @Test
    void friends_shouldOrderUnknownClosenessLast() {
        FriendsService service = serviceWith(NO_NAMES,
                friend("u1", "mystery", null),
                friend("u2", "known", "FRIEND"));

        StepVerifier.create(service.friends().map(Friend::userName))
                .expectNext("known", "mystery")
                .verifyComplete();
    }

    @Test
    void friends_shouldSortEquallyCloseFriendsByHandle() {
        FriendsService service = serviceWith(NO_NAMES,
                friend("u1", "zoe", "CLOSE"),
                friend("u2", "ana", "CLOSE"));

        StepVerifier.create(service.friends().map(Friend::userName))
                .expectNext("ana", "zoe")
                .verifyComplete();
    }

    @Test
    void friends_shouldPutTheResolvedNameOnEachFriend() {
        Map<String, String> names = Map.of("u1", "Camille Durand");
        FriendsService service = serviceWith(userId -> Mono.justOrEmpty(names.get(userId)),
                friend("u1", "camille", "CLOSE"));

        StepVerifier.create(service.friends())
                .assertNext(friend -> assertThat(friend.name()).isEqualTo("Camille Durand"))
                .verifyComplete();
    }

    @Test
    void friends_shouldStillListAFriendWithoutAName() {
        StepVerifier.create(serviceWith(NO_NAMES, friend("u1", "camille", "CLOSE")).friends())
                .assertNext(friend -> {
                    assertThat(friend.name()).isNull();
                    assertThat(friend.handle()).isEqualTo("camille#1002");
                })
                .verifyComplete();
    }

    @Test
    void friends_shouldPropagateAGraphFailure() {
        SocialGraph failing = () -> Flux.error(new IllegalStateException("social down"));

        StepVerifier.create(new FriendsService(failing, NO_NAMES).friends())
                .verifyError(IllegalStateException.class);
    }

    @Test
    void byHandle_shouldFindTheFriendThatHandleNames() {
        StepVerifier.create(serviceWith(NO_NAMES, friend("u1", "camille", "CLOSE")).byHandle("camille#1002"))
                .assertNext(friend -> assertThat(friend.userId()).isEqualTo("u1"))
                .verifyComplete();
    }

    @Test
    void byHandle_shouldIgnoreCaseAndSurroundingSpace() {
        StepVerifier.create(serviceWith(NO_NAMES, friend("u1", "camille", "CLOSE")).byHandle("  CAMILLE#1002 "))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void byHandle_shouldAcceptTheBareUserName() {
        StepVerifier.create(serviceWith(NO_NAMES, friend("u1", "camille", "CLOSE")).byHandle("camille"))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void byHandle_shouldBeEmptyForSomeoneWhoIsNotAFriend() {
        StepVerifier.create(serviceWith(NO_NAMES, friend("u1", "camille", "CLOSE")).byHandle("stranger#1"))
                .verifyComplete();
    }

    @Test
    void byHandle_shouldBeEmptyForABlankHandle() {
        StepVerifier.create(serviceWith(NO_NAMES, friend("u1", "camille", "CLOSE")).byHandle("   "))
                .verifyComplete();
    }
}
