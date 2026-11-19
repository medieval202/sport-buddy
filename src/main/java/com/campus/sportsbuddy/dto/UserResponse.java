package com.campus.sportsbuddy.dto;

import com.campus.sportsbuddy.model.Role;
import com.campus.sportsbuddy.model.SkillLevel;
import com.campus.sportsbuddy.model.User;

import java.util.Arrays;
import java.util.List;

public record UserResponse(
        Long id,
        String username,
        String displayName,
        Role role,
        List<String> sportPreferences,
        SkillLevel skillLevel,
        String campusZone,
        List<String> availableTimeSlots,
        Double latitude,
        Double longitude,
        Integer creditScore,
        boolean enabled
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRole(),
                split(user.getSportPreferences()),
                user.getSkillLevel(),
                user.getCampusZone(),
                split(user.getAvailableTimeSlots()),
                user.getLatitude(),
                user.getLongitude(),
                user.getCreditScore(),
                user.isEnabled()
        );
    }

    private static List<String> split(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }
}
