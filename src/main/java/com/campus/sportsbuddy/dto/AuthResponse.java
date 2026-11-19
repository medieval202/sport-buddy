package com.campus.sportsbuddy.dto;

public record AuthResponse(
        String token,
        UserResponse user
) {
}
