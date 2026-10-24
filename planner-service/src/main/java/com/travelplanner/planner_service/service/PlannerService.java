package com.travelplanner.planner_service.service;

import com.travelplanner.planner_service.model.DayPlan;
import com.travelplanner.planner_service.model.ItineraryResponse;
import com.travelplanner.planner_service.model.PlaceDto;
import com.travelplanner.planner_service.model.PreferenceDto;
import com.travelplanner.planner_service.model.TripDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PlannerService {

    private static final double STAY_BUDGET_SHARE = 0.6;

    private final RestClient tripClient;
    private final RestClient stayDiningClient;
    private final RestClient preferenceClient;

    public PlannerService(@Value("${trip-service.url}") String tripUrl,
                          @Value("${stay-dining-service.url}") String stayDiningUrl,
                          @Value("${preference-service.url}") String preferenceUrl) {
        this.tripClient = RestClient.create(tripUrl);
        this.stayDiningClient = RestClient.create(stayDiningUrl);
        this.preferenceClient = RestClient.create(preferenceUrl);
    }

        public ItineraryResponse buildItinerary(Long tripId, Set<Long> excluded) {
        List<String> notes = new ArrayList<>();

        // 1. Ask trip-service for the trip
        TripDto trip = tripClient.get()
                .uri("/api/trips/{id}", tripId)
                .retrieve()
                .body(TripDto.class);

        // 2. Ask preference-service for this trip's preferences (optional)
        PreferenceDto preference = fetchPreference(tripId, notes);

        // 3. Ask stay-dining-service for places in that destination
        List<PlaceDto> places = stayDiningClient.get()
                .uri("/api/places?destination={d}", trip.destination())
                .retrieve()
                .body(new ParameterizedTypeReference<List<PlaceDto>>() {});
        int totalPlaces = places.size();
        places = places.stream().filter(p -> !excluded.contains(p.id())).toList();
        if (places.size() < totalPlaces) {
            notes.add((totalPlaces - places.size()) + " place(s) were left out because of low traveler feedback.");
        }
        List<PlaceDto> hotels = places.stream()
                .filter(p -> "HOTEL".equals(p.type()))
                .toList();
        List<PlaceDto> allRestaurants = places.stream()
                .filter(p -> "RESTAURANT".equals(p.type()))
                .toList();

        // 4. Apply preferences to restaurants
        List<PlaceDto> restaurants = applyPreferences(allRestaurants, preference, notes);

        long totalDays = ChronoUnit.DAYS.between(trip.startDate(), trip.endDate()) + 1;
        int nights = (int) totalDays - 1;

        // 5. Choose a hotel
        PlaceDto hotel = chooseHotel(hotels, nights, trip.budget(), notes);
        double hotelCost = hotel == null ? 0 : hotel.price() * nights;

        // 6. Build the day-by-day plan
        List<DayPlan> days = new ArrayList<>();
        double mealsCost = 0;
        for (int i = 0; i < totalDays; i++) {
            LocalDate date = trip.startDate().plusDays(i);

            PlaceDto lunch = pickRestaurant(restaurants, 2 * i);
            PlaceDto dinner = pickRestaurant(restaurants, 2 * i + 1);

            if (lunch != null) {
                mealsCost += lunch.price() * trip.travelers();
            }
            if (dinner != null) {
                mealsCost += dinner.price() * trip.travelers();
            }

            boolean needsStay = hotel != null && i < nights;
            days.add(new DayPlan(
                    i + 1,
                    date,
                    needsStay ? hotel.name() : null,
                    lunch == null ? null : lunch.name(),
                    dinner == null ? null : dinner.name()));
        }

        if (allRestaurants.isEmpty()) {
            notes.add("No restaurants found for " + trip.destination() + ". Add some in stay-dining-service.");
        }
        if (hotel != null && trip.travelers() > 1) {
            notes.add("Hotel cost is per room. Extra rooms for " + trip.travelers() + " travelers are not included.");
        }
        if (trip.soloTraveler()) {
            notes.add("Solo traveler: you will be able to opt in to meet other travelers heading to "
                    + trip.destination() + " (coming soon).");
        }

        double total = hotelCost + mealsCost;
        boolean withinBudget = total <= trip.budget();
        if (!withinBudget) {
            notes.add("Estimated cost is above your budget by " + Math.round(total - trip.budget()) + ".");
        }

        return new ItineraryResponse(
                trip.id(),
                trip.destination(),
                trip.startDate(),
                trip.endDate(),
                nights,
                trip.travelers(),
                trip.soloTraveler(),
                hotel,
                days,
                hotelCost,
                mealsCost,
                total,
                trip.budget(),
                withinBudget,
                preference,
                notes);
    }

    private PreferenceDto fetchPreference(Long tripId, List<String> notes) {
        try {
            return preferenceClient.get()
                    .uri("/api/preferences/trip/{id}", tripId)
                    .retrieve()
                    .body(PreferenceDto.class);
        } catch (HttpClientErrorException.NotFound e) {
            return null; // no preferences saved, that is fine
        } catch (ResourceAccessException e) {
            notes.add("preference-service is not reachable, so default choices were used.");
            return null;
        }
    }

    private List<PlaceDto> applyPreferences(List<PlaceDto> restaurants, PreferenceDto pref, List<String> notes) {
        if (pref == null) {
            return restaurants.stream()
                    .sorted(Comparator.comparingDouble(PlaceDto::rating).reversed())
                    .toList();
        }

        Set<String> avoid = parse(pref.avoidCategories());
        Set<String> preferred = parse(pref.preferredCategories());

        List<PlaceDto> filtered = restaurants.stream()
                .filter(r -> !avoid.contains(lower(r.category())))
                .filter(r -> pref.maxMealPrice() <= 0 || r.price() <= pref.maxMealPrice())
                .filter(r -> r.rating() >= pref.minRating())
                .toList();

        int removed = restaurants.size() - filtered.size();
        if (removed > 0) {
            notes.add(removed + " restaurant(s) were left out because of your preferences.");
        }
        if (filtered.isEmpty() && !restaurants.isEmpty()) {
            notes.add("No restaurant matches your preferences. Try relaxing them.");
        }

        // preferred categories first, then best rating
        Comparator<PlaceDto> order = Comparator
                .comparing((PlaceDto r) -> !preferred.contains(lower(r.category())))
                .thenComparing(Comparator.comparingDouble(PlaceDto::rating).reversed());

        return filtered.stream().sorted(order).toList();
    }

    private Set<String> parse(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    private String lower(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private PlaceDto chooseHotel(List<PlaceDto> hotels, int nights, double budget, List<String> notes) {
        if (nights == 0) {
            return null;
        }
        if (hotels.isEmpty()) {
            notes.add("No hotels found for this destination. Add some in stay-dining-service.");
            return null;
        }

        double stayBudget = budget * STAY_BUDGET_SHARE;

        // best-rated hotel that fits within 60% of the budget
        PlaceDto best = hotels.stream()
                .filter(h -> h.price() * nights <= stayBudget)
                .max(Comparator.comparingDouble(PlaceDto::rating))
                .orElse(null);

        if (best != null) {
            return best;
        }

        notes.add("No hotel fits within the stay budget. Showing the cheapest option.");
        return hotels.stream()
                .min(Comparator.comparingDouble(PlaceDto::price))
                .orElse(null);
    }

    private PlaceDto pickRestaurant(List<PlaceDto> restaurants, int index) {
        if (restaurants.isEmpty()) {
            return null;
        }
        return restaurants.get(index % restaurants.size());
    }
}