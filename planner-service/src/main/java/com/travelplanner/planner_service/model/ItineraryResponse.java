package com.travelplanner.planner_service.model;

import java.time.LocalDate;
import java.util.List;

public record ItineraryResponse(
        Long tripId,
        String destination,
        LocalDate startDate,
        LocalDate endDate,
        int nights,
        int travelers,
        boolean soloTraveler,
        PlaceDto hotel,
        List<DayPlan> days,
        double hotelCost,
        double mealsCost,
        double totalEstimatedCost,
        double budget,
        boolean withinBudget,
        PreferenceDto appliedPreferences,
        List<String> notes) {
}   