package com.travelplanner.smart_planner_service.model;

import java.time.LocalDate;
import java.util.List;

public final class Models {

    private Models() {
    }

       public record SmartPlanRequest(
            String destination,
            LocalDate startDate,
            LocalDate endDate,
            Double maxHotelPerNight,
            List<String> amenities,
            Boolean nearAttractions,
            String pace,
            List<String> interests,
            List<String> avoid,
            Boolean forceIndoor) {
    }

    public record HotelOption(
            String name,
            int stars,
            double estimatedPricePerNight,
            double distanceToAttractionsKm,
            int fitScore,
            List<String> confirmedAmenities,
            List<String> unconfirmedAmenities,
            List<String> reasons,
            double lat,
            double lon,
            List<String> scoreBreakdown) {
    }

    public record DayWeather(
            LocalDate date,
            double highC,
            double rainMm,
            Integer rainChance,
            boolean rainy,
            boolean hot,
            String source) {
    }

    public record Leg(
            String to,
            double km,
            int minutes,
            int delayMinutes,
            String traffic,
            String source) {
    }

    public record Stop(
            String time,
            String name,
            String kind,
            String crowd,
            int crowdScore,
            String why,
            Leg travel) {
    }

    public record DayPlan(
            int day,
            LocalDate date,
            DayWeather weather,
            List<Stop> stops,
            List<String> tips) {
    }

    public record SmartPlan(
            String destination,
            HotelOption hotel,
            List<HotelOption> hotelOptions,
            List<DayPlan> days,
            List<String> notes,
            List<String> dataSources) {
    }

    public record JourneyRequest(String from, String to, LocalDate date) {
    }

    public record BreakStop(
            String name,
            double lat,
            double lon,
            int afterMinutes,
            String note) {
    }

    public record Journey(
            String from,
            String to,
            double km,
            int driveMinutes,
            int trafficDelayMinutes,
            String busEstimate,
            String trafficSource,
            List<BreakStop> breaks,
            String redbusUrl,
            List<String> tips,
            double arrivalLat,
            double arrivalLon) {
    }

    public record BookingRequest(
            String hotelName,
            String guestName,
            String phone,
            LocalDate checkIn,
            LocalDate checkOut,
            Integer rooms,
            Double pricePerNight) {
    }

    public record Booking(
            String reference,
            String hotelName,
            String guestName,
            LocalDate checkIn,
            LocalDate checkOut,
            int nights,
            int rooms,
            double estimatedTotal,
            String status,
            String note) {
    }
        public record FoodRequest(
            String destination,
            LocalDate startDate,
            LocalDate endDate,
            String diet,
            List<String> cuisines,
            Double maxPerMeal) {
    }

    public record Restaurant(
            String name,
            String cuisine,
            double estimatedPrice,
            double distanceKm,
            int fitScore,
            List<String> reasons,
            double avgRating,
            int reviewCount,
            double lat,
            double lon) {
    }

    public record MealDay(
            int day,
            LocalDate date,
            String lunch,
            String dinner) {
    }

    public record FoodPlan(
            String destination,
            List<Restaurant> restaurants,
            List<MealDay> meals,
            List<String> notes) {
    }

    public record ReviewRequest(
            String placeType,
            String placeName,
            String destination,
            Integer rating,
            String comment,
            String author) {
    }

    public record Review(
            String placeType,
            String placeName,
            String destination,
            int rating,
            String comment,
            String author,
            String createdAt) {
    }

    public record ReviewSummary(
            String placeName,
            double average,
            int count,
            List<Review> reviews) {
    }
        public record ReplanRequest(SmartPlanRequest plan, String reason, List<String> avoid) {
    }

    public record ReplanResult(
            String reason,
            String summary,
            List<String> changes,
            SmartPlan plan) {
    }

    public record ConnectProfileRequest(
            String displayName,
            String destination,
            LocalDate startDate,
            LocalDate endDate,
            List<String> interests,
            String pace) {
    }

    public record ConnectProfile(
            String id,
            String displayName,
            String destination,
            LocalDate startDate,
            LocalDate endDate,
            List<String> interests,
            String pace) {
    }

    public record ConnectMatch(
            String id,
            String displayName,
            LocalDate overlapStart,
            LocalDate overlapEnd,
            List<String> sharedInterests,
            boolean samePace,
            int compatibility,
            String pace,
            List<String> interests) {
    }

    public record MeetRequestBody(String toId, String message) {
    }

    public record MeetResponseBody(String responderId, String decision) {
    }

    public record MeetRequest(
            String id,
            String fromId,
            String fromName,
            String toId,
            String toName,
            String message,
            String status) {
    }
}