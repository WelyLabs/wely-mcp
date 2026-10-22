package com.calendar.mcp.domain.models;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class NewEventTest {

    private static NewEvent allDay(String start, String end) {
        return new NewEvent("Vacances", Instant.parse(start), end == null ? null : Instant.parse(end),
                "Niort", null, true);
    }

    @Test
    void normalised_shouldSnapAnAllDayEventToWholeUtcDays() {
        NewEvent event = allDay("2026-10-21T18:42:00Z", "2026-10-23T09:00:00Z");

        NewEvent result = event.normalised();

        assertThat(result.startDate()).isEqualTo(Instant.parse("2026-10-21T00:00:00Z"));
        assertThat(result.endDate()).isEqualTo(Instant.parse("2026-10-23T00:00:00Z"));
    }

    @Test
    void normalised_shouldGiveASingleAllDayEventTheMorningAfterAsItsEnd() {
        NewEvent result = allDay("2026-10-21T18:42:00Z", null).normalised();

        assertThat(result.startDate()).isEqualTo(Instant.parse("2026-10-21T00:00:00Z"));
        assertThat(result.endDate()).isEqualTo(Instant.parse("2026-10-22T00:00:00Z"));
    }

    @Test
    void normalised_shouldWidenAnAllDayEventThatStartsAndEndsTheSameDay() {
        NewEvent result = allDay("2026-10-21T08:00:00Z", "2026-10-21T20:00:00Z").normalised();

        assertThat(result.endDate()).isEqualTo(Instant.parse("2026-10-22T00:00:00Z"));
    }

    @Test
    void normalised_shouldLeaveATimedEventExactlyAsGiven() {
        NewEvent event = new NewEvent("Dîner", Instant.parse("2026-10-21T19:30:00Z"),
                Instant.parse("2026-10-21T22:15:00Z"), "Niort", "chez Paul", false);

        assertThat(event.normalised()).isEqualTo(event);
    }

    @Test
    void normalised_shouldKeepEverythingElseUntouched() {
        NewEvent result = new NewEvent("Vacances", Instant.parse("2026-10-21T18:42:00Z"), null,
                "Biarritz", "avec les cousins", true).normalised();

        assertThat(result.title()).isEqualTo("Vacances");
        assertThat(result.location()).isEqualTo("Biarritz");
        assertThat(result.description()).isEqualTo("avec les cousins");
        assertThat(result.allDay()).isTrue();
    }
}
