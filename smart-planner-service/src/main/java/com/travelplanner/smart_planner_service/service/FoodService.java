package com.travelplanner.smart_planner_service.service;

import com.travelplanner.smart_planner_service.model.Models.FoodPlan;
import com.travelplanner.smart_planner_service.model.Models.FoodRequest;
import com.travelplanner.smart_planner_service.model.Models.MealDay;
import com.travelplanner.smart_planner_service.model.Models.Restaurant;
import com.travelplanner.smart_planner_service.model.Models.ReviewSummary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class FoodService {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP =
            new ParameterizedTypeReference<>() {};

    private final RestClient geoapify = RestClient.builder().baseUrl("https://api.geoapify.com").build();
    private final String geoKey;
    private final ReviewService reviews;

    public FoodService(@Value("${geoapify.key}") String geoKey, ReviewService reviews) {
        this.geoKey = geoKey;
        this.reviews = reviews;
    }

    @SuppressWarnings("unchecked")
    public FoodPlan plan(FoodRequest req) {
        if (req.destination() == null || req.destination().isBlank() || req.startDate() == null
                || req.endDate() == null || req.endDate().isBefore(req.startDate())) {
            throw new IllegalArgumentException("Destination and valid start and end dates are required.");
        }
        int days = (int) ChronoUnit.DAYS.between(req.startDate(), req.endDate()) + 1;
        if (days > 10) {
            throw new IllegalArgumentException("Please plan at most 10 days at a time.");
        }
        String destination = req.destination().trim();
        String diet = req.diet() == null ? "any" : req.diet().trim().toLowerCase();
        List<String> wanted = req.cuisines() == null ? List.of() : req.cuisines().stream()
                .map(c -> c.trim().toLowerCase()).filter(c -> !c.isEmpty()).toList();
        Double max = req.maxPerMeal();
        List<String> notes = new ArrayList<>();

        double[] center = geocode(destination);
        Map<String, Object> body;
        try {
            body = geoapify.get()
                    .uri(u -> u.path("/v2/places")
                            .queryParam("categories", "catering.restaurant,catering.cafe")
                            .queryParam("filter", String.format(Locale.ROOT, "circle:%f,%f,15000", center[1], center[0]))
                            .queryParam("limit", 40)
                            .queryParam("apiKey", geoKey)
                            .build())
                    .retrieve()
                    .body(MAP);
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not load restaurants right now. Please try again.");
        }

        List<Restaurant> found = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        List<Map<String, Object>> features = (List<Map<String, Object>>) body.get("features");
        if (features != null) {
            for (Map<String, Object> f : features) {
                Map<String, Object> props = (Map<String, Object>) f.get("properties");
                Object name = props.get("name");
                if (name == null || name.toString().isBlank() || !seen.add(name.toString().toLowerCase())
                        || !(props.get("lat") instanceof Number lat) || !(props.get("lon") instanceof Number lon)) {
                    continue;
                }
                Map<String, Object> raw = rawTags(props);
                List<String> cats = props.get("categories") instanceof List<?> c
                        ? c.stream().map(Object::toString).toList() : List.of();
                String type = cats.isEmpty() ? "" : cats.get(cats.size() - 1);

                Object cuisineTag = raw.get("cuisine");
                String cuisine = cuisineTag != null && !cuisineTag.toString().isBlank()
                        ? cuisineTag.toString().replace(';', ',').replace('_', ' ')
                        : type.substring(type.lastIndexOf('.') + 1);
                String cuisineLower = cuisine.toLowerCase();

                double price = type.contains("cafe") ? 300 : type.contains("fast_food") ? 250 : 600;
                double dist = Math.round(km(lat.doubleValue(), lon.doubleValue(), center[0], center[1]) * 10) / 10.0;
                boolean vegConfirmed = isYesOrOnly(raw.get("diet:vegetarian")) || isYesOrOnly(raw.get("diet:vegan"));

                ReviewSummary rs = reviews.summary(name.toString(), destination);

                // four parts, 100 points in total
                int cuisinePts = wanted.isEmpty() ? 35 : wanted.stream().anyMatch(cuisineLower::contains) ? 35 : 0;
                int dietPts = !diet.equals("veg") ? 30 : vegConfirmed ? 30 : 18;
                int budgetPts = max == null ? 20 : price <= max ? (int) Math.round(10 + 10 * (1 - price / max)) : 0;
                int reviewPts = rs.count() > 0 ? (int) Math.round(3 * rs.average()) : 12;

                List<String> reasons = new ArrayList<>();
                reasons.add("Serves " + cuisine);
                if (diet.equals("veg")) {
                    reasons.add(vegConfirmed ? "Vegetarian options confirmed"
                            : "Vegetarian options not confirmed in map data, so please ask before ordering");
                }
                reasons.add("About ₹" + Math.round(price) + " per meal (estimated)");
                reasons.add(rs.count() > 0 ? "Rated " + rs.average() + " by " + rs.count() + " traveler(s)" : "No reviews yet");

                found.add(new Restaurant(name.toString(), cuisine, price, dist,
                        cuisinePts + dietPts + budgetPts + reviewPts, reasons,
                        rs.average(), rs.count(), lat.doubleValue(), lon.doubleValue()));
            }
        }

        List<Restaurant> pool = max == null ? found : found.stream().filter(r -> r.estimatedPrice() <= max).toList();
        if (pool.isEmpty() && !found.isEmpty()) {
            notes.add("Nothing is estimated to be within your meal budget, so the cheapest places are shown.");
            pool = found.stream().sorted(Comparator.comparingDouble(Restaurant::estimatedPrice)).limit(5).toList();
        }
        List<Restaurant> ranked = pool.stream()
                .sorted(Comparator.comparingInt(Restaurant::fitScore).reversed())
                .limit(10).toList();
        if (ranked.isEmpty()) {
            notes.add("No restaurants were found for " + destination + ".");
        }
        if (diet.equals("veg")) {
            notes.add("Map data often lacks diet tags, so vegetarian status is only confirmed for some places.");
        }

        List<MealDay> meals = new ArrayList<>();
        for (int d = 0; d < days && !ranked.isEmpty(); d++) {
            meals.add(new MealDay(d + 1, req.startDate().plusDays(d),
                    ranked.get((2 * d) % ranked.size()).name(),
                    ranked.get((2 * d + 1) % ranked.size()).name()));
        }
        return new FoodPlan(destination, ranked, meals, notes);
    }

    private boolean isYesOrOnly(Object v) {
        return v != null && (v.toString().equalsIgnoreCase("yes") || v.toString().equalsIgnoreCase("only"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> rawTags(Map<String, Object> props) {
        if (props.get("datasource") instanceof Map<?, ?> source && source.get("raw") instanceof Map<?, ?> raw) {
            return (Map<String, Object>) raw;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private double[] geocode(String place) {
        Map<String, Object> geo = geoapify.get()
                .uri(u -> u.path("/v1/geocode/search")
                        .queryParam("text", place)
                        .queryParam("limit", 1)
                        .queryParam("format", "json")
                        .queryParam("apiKey", geoKey)
                        .build())
                .retrieve()
                .body(MAP);
        List<Map<String, Object>> results = (List<Map<String, Object>>) geo.get("results");
        if (results == null || results.isEmpty()) {
            throw new IllegalArgumentException("Could not find a place called " + place);
        }
        return new double[]{((Number) results.get(0).get("lat")).doubleValue(),
                ((Number) results.get(0).get("lon")).doubleValue()};
    }

    private double km(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}