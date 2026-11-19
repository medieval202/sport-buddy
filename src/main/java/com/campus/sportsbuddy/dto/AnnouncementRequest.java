package com.campus.sportsbuddy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AnnouncementRequest(
        @NotBlank @Size(max = 120) String title,
        @NotBlank @Size(max = 2000) String content
) {
}
