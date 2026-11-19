package com.campus.sportsbuddy.dto;

import java.time.LocalTime;

public record BusySlotResponse(
        LocalTime startTime,
        LocalTime endTime
) {
}
