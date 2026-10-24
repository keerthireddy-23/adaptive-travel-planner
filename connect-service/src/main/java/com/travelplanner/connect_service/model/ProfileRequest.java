package com.travelplanner.connect_service.model;

import jakarta.validation.constraints.NotBlank;

public record ProfileRequest(
        @NotBlank(message = "Display name is required") String displayName,
        String interests) {
}