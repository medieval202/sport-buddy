package com.campus.sportsbuddy.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public record ReservationRequest(
        @NotNull Long venueId,
        Long activityId,
        @NotNull @FutureOrPresent LocalDate reservationDate,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime
) {
}
