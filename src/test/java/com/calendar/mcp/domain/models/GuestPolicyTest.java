package com.calendar.mcp.domain.models;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GuestPolicyTest {

    @Test
    void organizersFriends_shouldNameNoHonoree() {
        GuestPolicy policy = GuestPolicy.organizersFriends();

        assertThat(policy.surprise()).isFalse();
        assertThat(policy.namesAnHonoree()).isFalse();
    }

    @Test
    void honoreesFriends_shouldNameTheHonoree() {
        GuestPolicy policy = GuestPolicy.honoreesFriends("u1");

        assertThat(policy.surprise()).isTrue();
        assertThat(policy.honoreeId()).isEqualTo("u1");
        assertThat(policy.namesAnHonoree()).isTrue();
    }

    @Test
    void namesAnHonoree_shouldBeFalseForASurpriseWithoutOne() {
        assertThat(GuestPolicy.honoreesFriends(null).namesAnHonoree()).isFalse();
        assertThat(GuestPolicy.honoreesFriends("  ").namesAnHonoree()).isFalse();
    }
}
