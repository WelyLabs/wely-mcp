package com.calendar.mcp.domain.models;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FriendTest {

    @Test
    void handle_shouldJoinTheNameAndTheHashtag() {
        Friend friend = new Friend("u1", "camille", 1234, null, "CLOSE");

        assertThat(friend.handle()).isEqualTo("camille#1234");
    }

    @Test
    void handle_shouldFallBackToTheNameWhenThereIsNoHashtag() {
        Friend friend = new Friend("u1", "camille", null, null, "CLOSE");

        assertThat(friend.handle()).isEqualTo("camille");
    }

    @Test
    void named_shouldKeepEverythingButTheName() {
        Friend friend = new Friend("u1", "camille", 1234, null, "INTIMATE").named("Camille Durand");

        assertThat(friend.name()).isEqualTo("Camille Durand");
        assertThat(friend.userId()).isEqualTo("u1");
        assertThat(friend.handle()).isEqualTo("camille#1234");
        assertThat(friend.closeness()).isEqualTo("INTIMATE");
    }
}
