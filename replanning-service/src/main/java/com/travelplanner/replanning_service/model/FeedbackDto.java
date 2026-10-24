package com.travelplanner.replanning_service.model;

public record FeedbackDto(
        Long id,
        Long tripId,
        Long placeId,
        String placeName,
        int rating,
        String comment,
        String createdAt) {
}