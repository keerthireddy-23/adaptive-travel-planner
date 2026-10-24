package com.travelplanner.connect_service.model;

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