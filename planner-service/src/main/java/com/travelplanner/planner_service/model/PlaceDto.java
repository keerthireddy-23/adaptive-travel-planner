package com.travelplanner.planner_service.model;

public record PlaceDto(
        Long id,
        String name,
        String destination,
        String type,
        double price,
        double rating,
        String category) {
}