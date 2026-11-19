package com.campus.sportsbuddy.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RadarStartRequest(
        @NotNull @DecimalMin("0.5") @DecimalMax("20.0") Double radiusKm,
        @NotNull @Min(3) @Max(10) Integer topN,
        Boolean simulationMode
) {
}
