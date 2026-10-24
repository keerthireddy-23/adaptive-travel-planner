package com.travelplanner.connect_service.model;

import java.time.LocalDate;
import java.util.List;

public record MatchDto(
        String displayName,
        Long tripId,
        String interests,
        List<String> sharedInterests,
        LocalDate overlapStart,
        LocalDate overlapEnd) {
}