package com.travelplanner.planner_service.model;

public record PreferenceDto(
        Long id,
        Long tripId,
        String preferredCategories,
        String avoidCategories,
        double maxMealPrice,
        double minRating) {
}