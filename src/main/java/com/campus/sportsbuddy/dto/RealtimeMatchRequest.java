package com.campus.sportsbuddy.dto;

import com.campus.sportsbuddy.model.MatchTimeWindow;
import com.campus.sportsbuddy.model.SkillLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RealtimeMatchRequest(
        @NotBlank @Size(max = 30) String sportType,
        @NotNull MatchTimeWindow timeWindow,
        @NotBlank @Size(max = 50) String campusZone,
        @NotNull SkillLevel skillLevel,
        @NotNull Double latitude,
        @NotNull Double longitude
) {
}
