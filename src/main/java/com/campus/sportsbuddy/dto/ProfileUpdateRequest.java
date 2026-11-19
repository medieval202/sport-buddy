package com.campus.sportsbuddy.dto;

import com.campus.sportsbuddy.model.SkillLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ProfileUpdateRequest(
        @NotBlank @Size(max = 50) String displayName,
        @NotEmpty List<String> sportPreferences,
        SkillLevel skillLevel,
        @NotBlank String campusZone,
        @NotEmpty List<String> availableTimeSlots,
        Double latitude,
        Double longitude
) {
}
