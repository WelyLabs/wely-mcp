package com.calendar.mcp.domain.models;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * An event about to be created. Visibility is deliberately absent: the calendar defaults to FRIENDS,
 * and a model should not be the one deciding a privacy setting from a sentence.
 */
public record NewEvent(
        String title,
        Instant startDate,
        Instant endDate,
        String location,
        String description,
        boolean allDay) {

    /**
     * Whole UTC days, as the calendar stores them: start at 00:00Z of the first day, end at 00:00Z
     * of the day after the last. Normalised here so a model cannot produce an all-day event the API
     * would refuse, nor one that silently spans the wrong days.
     */
    public NewEvent normalised() {
        if (!allDay) {
            return this;
        }
        Instant start = startDate.truncatedTo(ChronoUnit.DAYS);
        Instant end = endDate == null ? start.plus(1, ChronoUnit.DAYS) : endDate.truncatedTo(ChronoUnit.DAYS);
        if (!end.isAfter(start)) {
            // A single day given as "the 21st to the 21st": the calendar wants the exclusive morning after.
            end = start.plus(1, ChronoUnit.DAYS);
        }
        return new NewEvent(title, start, end, location, description, true);
    }
}
