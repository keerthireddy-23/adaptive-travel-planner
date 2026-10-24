package com.travelplanner.planner_service.model;

import java.time.LocalDate;

public record TripDto(
        Long id,
        String destination,
        LocalDate startDate,
        LocalDate endDate,
        double budget,
        int travelers,
        boolean soloTraveler) {
}
