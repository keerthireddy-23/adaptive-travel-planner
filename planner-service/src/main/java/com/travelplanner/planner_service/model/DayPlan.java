package com.travelplanner.planner_service.model;

import java.time.LocalDate;

public record DayPlan(
        int dayNumber,
        LocalDate date,
        String stay,
        String lunch,
        String dinner) {
}