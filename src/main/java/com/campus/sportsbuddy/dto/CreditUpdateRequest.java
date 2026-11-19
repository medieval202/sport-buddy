package com.campus.sportsbuddy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record CreditUpdateRequest(
        @Min(0) @Max(100) Integer creditScore,
        Boolean enabled
) {
}
