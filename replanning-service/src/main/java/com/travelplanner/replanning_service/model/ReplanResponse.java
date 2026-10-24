package com.travelplanner.replanning_service.model;

import java.util.List;
import java.util.Map;

public record ReplanResponse(
        Long tripId,
        String summary,
        List<String> avoidedPlaces,
        List<String> changes,
        Map<String, Object> newPlan) {
}