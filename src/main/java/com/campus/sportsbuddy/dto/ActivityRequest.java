package com.campus.sportsbuddy.dto;

import com.campus.sportsbuddy.model.SkillLevel;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record ActivityRequest(
        @NotBlank @Size(max = 100) String title,
        @NotBlank @Size(max = 30) String sportType,
        @Size(max = 1000) String description,
        @NotBlank @Size(max = 50) String campusZone,
        @NotBlank @Size(max = 100) String locationName,
        @NotNull LocalDateTime startTime,
        @NotNull LocalDateTime endTime,
        @NotNull @Min(2) @Max(100) Integer maxParticipants,
        @NotNull SkillLevel requiredSkillLevel,
        @NotNull @Min(0) @Max(100) Integer minCreditScore,
        @NotNull Double latitude,
        @NotNull Double longitude
) {
}
