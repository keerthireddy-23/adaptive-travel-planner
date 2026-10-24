package com.travelplanner.destination_service.service;

import com.travelplanner.destination_service.model.DestinationInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class DestinationService {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient geoapify = RestClient.builder().baseUrl("https://api.geoapify.com").build();
    private final RestClient weather = RestClient.builder().baseUrl("https://archive-api.open-meteo.com").build();
    private final String apiKey;

    public DestinationService(@Value("${geoapify.key}") String apiKey) {
        this.apiKey = apiKey;
    }

    @SuppressWarnings("unchecked")
    public DestinationInfo lookup(String destination, LocalDate start, LocalDate end) {
        // 1. Find the destination
        Map<String, Object> geo = geoapify.get()
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
            throw new DestinationNotFoundException(destination);
        }
        double lat = ((Number) results.get(0).get("lat")).doubleValue();
        double lon = ((Number) results.get(0).get("lon")).doubleValue();
        String resolved = String.valueOf(results.get(0).get("formatted"));

        // 2. Weather observed on the same dates one year earlier
        LocalDate pastStart = start.minusYears(1);
        LocalDate pastEnd = end.minusYears(1);
        Map<String, Object> w = weather.get()
                .uri(u -> u.path("/v1/archive")
                        .queryParam("latitude", lat)
                        .queryParam("longitude", lon)
                        .queryParam("start_date", pastStart.toString())
                        .queryParam("end_date", pastEnd.toString())
                        .queryParam("daily", "temperature_2m_max,temperature_2m_min,precipitation_sum")
                        .queryParam("timezone", "auto")
                        .build())
                .retrieve()
                .body(MAP_TYPE);

        Map<String, Object> daily = (Map<String, Object>) w.get("daily");
        List<Number> highs = (List<Number>) daily.get("temperature_2m_max");
        List<Number> lows = (List<Number>) daily.get("temperature_2m_min");
        List<Number> rain = (List<Number>) daily.get("precipitation_sum");

        double avgHigh = round(average(highs));
        double avgLow = round(average(lows));
        double rainTotal = round(rain.stream().filter(n -> n != null).mapToDouble(Number::doubleValue).sum());
        long rainyDays = rain.stream().filter(n -> n != null && n.doubleValue() >= 1.0).count();

        return new DestinationInfo(
                destination,
                resolved,
                lat,
                lon,
                "Weather recorded on the same dates last year (Open-Meteo)",
                avgHigh,
                avgLow,
                rainTotal,
                rainyDays,
                rain.size(),
                advice(avgHigh, avgLow, rainyDays, rain.size()),
                attractions(lat, lon));
    }

    @SuppressWarnings("unchecked")
    private List<String> attractions(double lat, double lon) {
        String filter = String.format(Locale.ROOT, "circle:%f,%f,25000", lon, lat);
        Map<String, Object> body = geoapify.get()
                .uri(u -> u.path("/v2/places")
                        .queryParam("categories", "tourism.attraction")
                        .queryParam("filter", filter)
                        .queryParam("limit", 12)
                        .queryParam("apiKey", apiKey)
                        .build())
                .retrieve()
                .body(MAP_TYPE);

        List<String> names = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        List<Map<String, Object>> features = (List<Map<String, Object>>) body.get("features");
        if (features != null) {
            for (Map<String, Object> f : features) {
                Map<String, Object> props = (Map<String, Object>) f.get("properties");
                Object name = props.get("name");
                if (name != null && !name.toString().isBlank() && seen.add(name.toString().toLowerCase())) {
                    names.add(name.toString());
                }
            }
        }
        return names;
    }

    private String advice(double high, double low, long rainyDays, int days) {
        List<String> tips = new ArrayList<>();
        if (days > 0 && rainyDays * 100 / days >= 40) {
            tips.add("Expect frequent rain, so pack rain gear and keep indoor options ready.");
        } else if (rainyDays > 0) {
            tips.add("A few rainy days are possible.");
        } else {
            tips.add("Mostly dry weather is likely.");
        }
        if (high >= 33) {
            tips.add("It can get hot, so plan outdoor activities for the morning or evening.");
        } else if (high <= 15) {
            tips.add("It is cold, so pack warm clothes.");
        } else {
            tips.add("Temperatures are comfortable for sightseeing.");
        }
        return String.join(" ", tips);
    }

    private double average(List<Number> values) {
        return values.stream().filter(n -> n != null).mapToDouble(Number::doubleValue).average().orElse(0);
    }

    private double round(double v) {
        return Math.round(v * 10) / 10.0;
    }
}