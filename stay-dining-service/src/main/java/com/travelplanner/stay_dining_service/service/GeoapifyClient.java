package com.travelplanner.stay_dining_service.service;

import com.travelplanner.stay_dining_service.model.Place;
import com.travelplanner.stay_dining_service.model.PlaceType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GeoapifyClient {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient client = RestClient.builder()
            .baseUrl("https://api.geoapify.com")
            .build();

    private final String apiKey;

    public GeoapifyClient(@Value("${geoapify.key}") String apiKey) {
        this.apiKey = apiKey;
    }

    @SuppressWarnings("unchecked")
    public List<Place> fetchPlaces(String destination) {
        // 1. Turn the destination name into coordinates
        Map<String, Object> geo = client.get()
                .uri(u -> u.path("/v1/geocode/search")
                        .queryParam("text", destination)
                        .queryParam("limit", 1)
                        .queryParam("format", "json")
                        .queryParam("apiKey", apiKey)
                        .build())
                .retrieve()
                .body(MAP_TYPE);

        List<Map<String, Object>> results = (List<Map<String, Object>>) geo.get("results");
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        double lat = ((Number) results.get(0).get("lat")).doubleValue();
        double lon = ((Number) results.get(0).get("lon")).doubleValue();

        // 2. Find hotels and restaurants around that point
        List<Place> places = new ArrayList<>();
        places.addAll(search("accommodation.hotel", PlaceType.HOTEL, destination, lat, lon, 8));
        places.addAll(search("catering.restaurant,catering.cafe", PlaceType.RESTAURANT, destination, lat, lon, 15));
        return places;
    }

    @SuppressWarnings("unchecked")
    private List<Place> search(String categories, PlaceType type, String destination,
                               double lat, double lon, int limit) {
        String filter = String.format(Locale.ROOT, "circle:%f,%f,20000", lon, lat);

        Map<String, Object> body = client.get()
                .uri(u -> u.path("/v2/places")
                        .queryParam("categories", categories)
                        .queryParam("filter", filter)
                        .queryParam("limit", limit)
                        .queryParam("apiKey", apiKey)
                        .build())
                .retrieve()
                .body(MAP_TYPE);

        List<Place> out = new ArrayList<>();
        List<Map<String, Object>> features = (List<Map<String, Object>>) body.get("features");
        if (features == null) {
            return out;
        }

        Set<String> seen = new HashSet<>();
        for (Map<String, Object> feature : features) {
            Map<String, Object> props = (Map<String, Object>) feature.get("properties");
            Object name = props.get("name");
            if (name == null || name.toString().isBlank() || !seen.add(name.toString().toLowerCase())) {
                continue;
            }

            Map<String, Object> raw = rawTags(props);
            Place place = new Place();
            place.setName(name.toString());
            place.setDestination(destination);
            place.setType(type);
            place.setCategory(label(props, raw));

            if (type == PlaceType.HOTEL) {
                int stars = stars(raw);
                place.setRating(stars > 0 ? stars : 3.5);
                place.setPrice(hotelPrice(stars));
            } else {
                place.setRating(4.0);
                place.setPrice(mealPrice(place.getCategory()));
            }
            out.add(place);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> rawTags(Map<String, Object> props) {
        if (props.get("datasource") instanceof Map<?, ?> source
                && source.get("raw") instanceof Map<?, ?> raw) {
            return (Map<String, Object>) raw;
        }
        return Map.of();
    }

    private String label(Map<String, Object> props, Map<String, Object> raw) {
        Object cuisine = raw.get("cuisine");
        if (cuisine != null && !cuisine.toString().isBlank()) {
            return cuisine.toString().split(";")[0].trim();
        }
        if (props.get("categories") instanceof List<?> list && !list.isEmpty()) {
            String last = list.get(list.size() - 1).toString();
            return last.substring(last.lastIndexOf('.') + 1);
        }
        return "";
    }

    private int stars(Map<String, Object> raw) {
        Object value = raw.get("stars");
        if (value == null) {
            return 0;
        }
        Matcher m = Pattern.compile("\\d").matcher(value.toString());
        return m.find() ? Math.min(5, Integer.parseInt(m.group())) : 0;
    }

    // Geoapify does not return prices, so these are rough estimates in rupees
    private double hotelPrice(int stars) {
        return switch (stars) {
            case 1 -> 1200;
            case 2 -> 1800;
            case 3 -> 2800;
            case 4 -> 5000;
            case 5 -> 9000;
            default -> 2500;
        };
    }

    private double mealPrice(String category) {
        String c = category == null ? "" : category.toLowerCase();
        if (c.contains("cafe")) {
            return 300;
        }
        if (c.contains("fast_food")) {
            return 250;
        }
        return 600;
    }
}