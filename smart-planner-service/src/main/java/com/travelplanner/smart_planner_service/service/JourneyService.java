package com.travelplanner.smart_planner_service.service;

import com.travelplanner.smart_planner_service.model.Models.BreakStop;
import com.travelplanner.smart_planner_service.model.Models.Journey;
import com.travelplanner.smart_planner_service.model.Models.JourneyRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class JourneyService {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP =
            new ParameterizedTypeReference<>() {};
    private static final int BREAK_EVERY_MIN = 180;

    private final RestClient geoapify = RestClient.builder().baseUrl("https://api.geoapify.com").build();
    private final RestClient http = RestClient.create();
    private final String geoKey;
    private final String tomtomKey;

    public JourneyService(@Value("${geoapify.key}") String geoKey,
                          @Value("${tomtom.key}") String tomtomKey) {
        this.geoKey = geoKey;
        this.tomtomKey = tomtomKey;
    }

    @SuppressWarnings("unchecked")
    public Journey plan(JourneyRequest req) {
        if (req.from() == null || req.from().isBlank() || req.to() == null || req.to().isBlank()) {
            throw new IllegalArgumentException("Both 'from' and 'to' are required.");
        }
        double[] a = geocode(req.from().trim());
        double[] b = geocode(req.to().trim());

        List<double[]> points = new ArrayList<>();
        double km = 0;
        int minutes = 0;
        int delay = 0;
        String source;
        List<String> tips = new ArrayList<>();

        try {
            String[] used = new String[1];
            Map<String, Object> r = route(a, b, req.date(), used);
            source = used[0];
            Map<String, Object> route0 = ((List<Map<String, Object>>) r.get("routes")).get(0);
            Map<String, Object> sum = (Map<String, Object>) route0.get("summary");
            km = round(((Number) sum.get("lengthInMeters")).doubleValue() / 1000);
            minutes = Math.round(((Number) sum.get("travelTimeInSeconds")).floatValue() / 60);
            delay = sum.get("trafficDelayInSeconds") instanceof Number n ? Math.round(n.floatValue() / 60) : 0;
            for (Map<String, Object> leg : (List<Map<String, Object>>) route0.get("legs")) {
                for (Map<String, Object> p : (List<Map<String, Object>>) leg.get("points")) {
                    points.add(new double[]{((Number) p.get("latitude")).doubleValue(),
                            ((Number) p.get("longitude")).doubleValue()});
                }
            }
        } catch (Exception e) {
            km = round(haversine(a, b) * 1.35);
            minutes = (int) Math.round(km / 50 * 60);
            for (int i = 0; i <= 20; i++) {
                points.add(new double[]{a[0] + (b[0] - a[0]) * i / 20, a[1] + (b[1] - a[1]) * i / 20});
            }
            source = "estimated from straight-line distance (TomTom error: " + describe(e) + ")";
        }

        List<BreakStop> breaks = new ArrayList<>();
        if (minutes > BREAK_EVERY_MIN + 30 && points.size() > 1) {
            int count = (minutes - 30) / BREAK_EVERY_MIN;
            for (int k = 1; k <= count; k++) {
                double frac = (double) (k * BREAK_EVERY_MIN) / minutes;
                double[] p = points.get(Math.min(points.size() - 1, (int) Math.round(frac * (points.size() - 1))));
                String name = restStop(p);
                breaks.add(new BreakStop(
                        name != null ? name : "Rest stop around km " + Math.round(km * frac),
                        p[0], p[1], k * BREAK_EVERY_MIN,
                        "About " + (k * BREAK_EVERY_MIN / 60) + " h into the journey. Stretch, eat, and use the washroom."));
            }
        }

        int busMinutes = (int) Math.round(minutes * 1.15) + breaks.size() * 20;
        String busEstimate = "about " + busMinutes / 60 + " h " + busMinutes % 60 + " min (road time plus stops, estimated)";

        tips.add("The redBus button opens the route search. Choose your travel date there.");
        if (busMinutes > 480) {
            tips.add("Long journey: overnight sleeper buses are common on this route. Check departure times on redBus.");
        }
        if (!breaks.isEmpty()) {
            tips.add("Take a 15 to 20 minute break about every 3 hours.");
        }

        String redbus = "https://www.redbus.in/bus-tickets/" + slug(req.from()) + "-to-" + slug(req.to());
        return new Journey(req.from().trim(), req.to().trim(), km, minutes, delay, busEstimate, source,
                breaks, redbus, tips, b[0], b[1]);
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

    private Map<String, Object> route(double[] a, double[] b, LocalDate date, String[] used) {
        String loc = String.format(Locale.ROOT, "%f,%f:%f,%f", a[0], a[1], b[0], b[1]);
        String base = "https://api.tomtom.com/routing/1/calculateRoute/" + loc
                + "/json?key=" + tomtomKey + "&traffic=true&travelMode=car&computeTravelTimeFor=all";
        if (date != null && !date.isBefore(LocalDate.now())) {
            try {
                Map<String, Object> r = http.get()
                        .uri(URI.create(base + "&departAt=" + date + "T08:00:00%2B05:30"))
                        .retrieve().body(MAP);
                used[0] = "TomTom (predicted traffic for an 08:00 departure)";
                return r;
            } catch (Exception ignored) {
                // fall back to current traffic below
            }
        }
        Map<String, Object> r = http.get().uri(URI.create(base)).retrieve().body(MAP);
        used[0] = "TomTom (current traffic)";
        return r;
    }

    @SuppressWarnings("unchecked")
    private String restStop(double[] p) {
        try {
            Map<String, Object> body = geoapify.get()
                    .uri(u -> u.path("/v2/places")
                            .queryParam("categories", "catering.restaurant,catering.cafe")
                            .queryParam("filter", String.format(Locale.ROOT, "circle:%f,%f,8000", p[1], p[0]))
                            .queryParam("limit", 5)
                            .queryParam("apiKey", geoKey)
                            .build())
                    .retrieve()
                    .body(MAP);
            for (Map<String, Object> f : (List<Map<String, Object>>) body.get("features")) {
                Object name = ((Map<String, Object>) f.get("properties")).get("name");
                if (name != null && !name.toString().isBlank()) {
                    return name.toString();
                }
            }
        } catch (Exception ignored) {
            // no named stop found
        }
        return null;
    }

    private String slug(String s) {
        return s.split(",")[0].trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    private double haversine(double[] a, double[] b) {
        double dLat = Math.toRadians(b[0] - a[0]);
        double dLon = Math.toRadians(b[1] - a[1]);
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(a[0])) * Math.cos(Math.toRadians(b[0])) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6371 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }

    private double round(double v) {
        return Math.round(v * 10) / 10.0;
    }

    private String describe(Exception e) {
        String m = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        if (!tomtomKey.isBlank()) {
            m = m.replace(tomtomKey, "***");
        }
        if (!geoKey.isBlank()) {
            m = m.replace(geoKey, "***");
        }
        return m.length() > 160 ? m.substring(0, 160) + "..." : m;
    }
}