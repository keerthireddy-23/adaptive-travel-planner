package com.travelplanner.smart_planner_service.service;

import com.travelplanner.smart_planner_service.model.Models.DayPlan;
import com.travelplanner.smart_planner_service.model.Models.DayWeather;
import com.travelplanner.smart_planner_service.model.Models.HotelOption;
import com.travelplanner.smart_planner_service.model.Models.Leg;
import com.travelplanner.smart_planner_service.model.Models.SmartPlan;
import com.travelplanner.smart_planner_service.model.Models.SmartPlanRequest;
import com.travelplanner.smart_planner_service.model.Models.Stop;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SmartPlannerService {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP =
            new ParameterizedTypeReference<>() {};
    private static final Pattern SHOP_NAME =
            Pattern.compile("\\b(shop|store|emporium|boutique|handicrafts?)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern STAR_NAME =
            Pattern.compile("(\\d)\\s*(\\*|-?\\s?star)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CALM_NAME =
            Pattern.compile("resort|retreat|villa|spa|ayurved|nature|yoga|beach", Pattern.CASE_INSENSITIVE);

    private record Spot(String name, double lat, double lon, boolean indoor, boolean notable, String type) {
    }

    private final RestClient geoapify = RestClient.builder().baseUrl("https://api.geoapify.com").build();
    private final RestClient http = RestClient.create();
    private final String geoKey;
    private final String tomtomKey;

    public SmartPlannerService(@Value("${geoapify.key}") String geoKey,
                               @Value("${tomtom.key}") String tomtomKey) {
        this.geoKey = geoKey;
        this.tomtomKey = tomtomKey;
    }

    public SmartPlan plan(SmartPlanRequest req) {
        if (req.destination() == null || req.destination().isBlank() || req.startDate() == null
                || req.endDate() == null || req.endDate().isBefore(req.startDate())) {
            throw new IllegalArgumentException("Destination and valid start and end dates are required.");
        }
        int days = (int) ChronoUnit.DAYS.between(req.startDate(), req.endDate()) + 1;
        if (days > 10) {
            throw new IllegalArgumentException("Please plan at most 10 days at a time.");
        }
        String pace = req.pace() == null ? "explore" : req.pace().trim().toLowerCase();
        if (!List.of("rest", "light", "explore").contains(pace)) {
            pace = "explore";
        }
        List<String> interests = req.interests() == null ? List.of() : req.interests().stream()
                .map(i -> i.trim().toLowerCase()).filter(i -> !i.isEmpty()).toList();
        String[] times = timesFor(pace);

        List<String> notes = new ArrayList<>();
        notes.add(switch (pace) {
            case "rest" -> "Relaxed pace: one easy stop per day, with plenty of time to rest.";
            case "light" -> "Light pace: two stops per day with a long break in between.";
            default -> "Explorer pace: three stops per day.";
        });
        double[] center = geocode(req.destination().trim());

        List<Spot> outdoor = spots("tourism.attraction,leisure.park,beach", false, center, 30);
        List<Spot> indoor = spots("entertainment.museum,entertainment.culture,commercial.shopping_mall,commercial.marketplace",
                true, center, 25);
        if (outdoor.isEmpty() && indoor.isEmpty()) {
            throw new IllegalArgumentException("No attractions found near " + req.destination());
        }
        if (indoor.isEmpty()) {
            notes.add("No indoor places were found, so rainy days may still include outdoor stops.");
        }

        List<Spot> all = new ArrayList<>(outdoor);
        all.addAll(indoor);
        double cLat = all.stream().mapToDouble(Spot::lat).average().orElse(center[0]);
        double cLon = all.stream().mapToDouble(Spot::lon).average().orElse(center[1]);

        List<HotelOption> hotels = hotels(req, pace, center, cLat, cLon, notes);
        HotelOption hotel = hotels.isEmpty() ? null : hotels.get(0);
        if (hotel == null) {
            notes.add("No hotel could be chosen, so days start from the city center.");
        }

        List<DayWeather> weather = weather(center[0], center[1], req.startDate(), req.endDate(), notes);

                Set<String> used = new HashSet<>(req.avoid() == null ? List.of() : req.avoid());
        List<DayPlan> plan = new ArrayList<>();
        String trafficReason = null;

        for (int d = 0; d < days; d++) {
            LocalDate date = req.startDate().plusDays(d);
            DayWeather w = d < weather.size() ? weather.get(d) : null;
            double[] prev = hotel == null ? center : new double[]{hotel.lat(), hotel.lon()};
            List<Stop> stops = new ArrayList<>();
            int heavy = 0;

            for (String time : times) {
                int slot = slotOf(time);
                boolean rainy = w != null && w.rainy();
                boolean hotAfternoon = w != null && w.hot() && slot == 1;
                                boolean wantIndoor = rainy || hotAfternoon || Boolean.TRUE.equals(req.forceIndoor());

                Spot s = wantIndoor
                        ? pick(indoor, outdoor, used, date, slot, prev, interests)
                        : pick(outdoor, indoor, used, date, slot, prev, interests);
                if (s == null) {
                    break;
                }
                used.add(s.name());

                int crowdScore = crowd(s, date, slot);
                Leg leg = trafficReason != null
                        ? estimate(prev, s, trafficReason, time)
                        : traffic(prev, s, date, time);
                if (trafficReason == null && leg.source().startsWith("estimated")) {
                    trafficReason = leg.source();
                }
                if (leg.traffic().equals("Heavy")) {
                    heavy++;
                }

                stops.add(new Stop(time, s.name(), s.indoor() ? "indoor" : "outdoor",
                        crowdLabel(crowdScore), crowdScore,
                        why(s, slot, rainy, hotAfternoon, crowdScore, matches(s, interests)), leg));
                prev = new double[]{s.lat(), s.lon()};
            }

            List<String> tips = new ArrayList<>();
            if (w != null && w.rainy()) {
                tips.add("Rain is likely, so carry an umbrella.");
            }
            if (w != null && w.hot()) {
                tips.add("It will be hot, so drink water and use sunscreen.");
            }
            if (heavy > 0) {
                tips.add("Heavy traffic on " + heavy + " leg(s). Leaving earlier helps.");
            }
            plan.add(new DayPlan(d + 1, date, w, stops, tips));
        }

        if (trafficReason != null) {
            notes.add("Live traffic was unavailable, so traffic is estimated from the time of day. Reason: " + trafficReason);
        }

        return new SmartPlan(req.destination().trim(), hotel, hotels, plan, notes, List.of(
                "Places and hotels: Geoapify open map data (hotel prices are estimated from star ratings)",
                "Weather: Open-Meteo (forecast within 16 days, otherwise last year's records for the same dates)",
                "Traffic: TomTom routing with traffic (time-of-day estimate if unavailable)",
                "Crowdedness: estimated from time of day, weekday and how well known a place is"));
    }

    private String[] timesFor(String pace) {
        return switch (pace) {
            case "rest" -> new String[]{"11:00"};
            case "light" -> new String[]{"10:00", "16:30"};
            default -> new String[]{"09:00", "14:00", "17:30"};
        };
    }

    private int slotOf(String time) {
        int hour = Integer.parseInt(time.substring(0, 2));
        return hour < 12 ? 0 : hour < 16 ? 1 : 2;
    }

    // ---------- Geoapify ----------

    @SuppressWarnings("unchecked")
    private double[] geocode(String destination) {
        Map<String, Object> geo = geoapify.get()
                .uri(u -> u.path("/v1/geocode/search")
                        .queryParam("text", destination)
                        .queryParam("limit", 1)
                        .queryParam("format", "json")
                        .queryParam("apiKey", geoKey)
                        .build())
                .retrieve()
                .body(MAP);
        List<Map<String, Object>> results = (List<Map<String, Object>>) geo.get("results");
        if (results == null || results.isEmpty()) {
            throw new IllegalArgumentException("Could not find a place called " + destination);
        }
        return new double[]{((Number) results.get(0).get("lat")).doubleValue(),
                ((Number) results.get(0).get("lon")).doubleValue()};
    }

    @SuppressWarnings("unchecked")
    private List<Spot> spots(String categories, boolean indoor, double[] center, int limit) {
        Map<String, Object> body;
        try {
            body = geoapify.get()
                    .uri(u -> u.path("/v2/places")
                            .queryParam("categories", categories)
                            .queryParam("filter", String.format(Locale.ROOT, "circle:%f,%f,25000", center[1], center[0]))
                            .queryParam("limit", limit)
                            .queryParam("apiKey", geoKey)
                            .build())
                    .retrieve()
                    .body(MAP);
        } catch (Exception e) {
            return List.of();
        }
        List<Spot> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        List<Map<String, Object>> features = (List<Map<String, Object>>) body.get("features");
        if (features == null) {
            return out;
        }
        for (Map<String, Object> f : features) {
            Map<String, Object> props = (Map<String, Object>) f.get("properties");
            Object name = props.get("name");
            if (name == null || name.toString().isBlank() || !seen.add(name.toString().toLowerCase())
                    || !(props.get("lat") instanceof Number lat) || !(props.get("lon") instanceof Number lon)) {
                continue;
            }
            List<String> cats = props.get("categories") instanceof List<?> c
                    ? c.stream().map(Object::toString).toList() : List.of();
            boolean smallShop = cats.stream().anyMatch(c -> c.startsWith("commercial")
                    && !c.equals("commercial.shopping_mall") && !c.equals("commercial.marketplace"));
            if (smallShop || SHOP_NAME.matcher(name.toString()).find()) {
                continue;
            }
            String type = cats.isEmpty() ? "" : cats.get(cats.size() - 1);
            out.add(new Spot(name.toString(), lat.doubleValue(), lon.doubleValue(), indoor,
                    props.containsKey("wiki_and_media"), type));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<HotelOption> hotels(SmartPlanRequest req, String pace, double[] center,
                                     double cLat, double cLon, List<String> notes) {
        List<String> wanted = req.amenities() == null ? List.of() : req.amenities().stream()
                .map(a -> a.trim().toLowerCase()).filter(a -> !a.isEmpty()).toList();
        boolean near = req.nearAttractions() == null || req.nearAttractions();
        Double max = req.maxHotelPerNight();

        Map<String, Object> body;
        try {
            body = geoapify.get()
                    .uri(u -> u.path("/v2/places")
                            .queryParam("categories", "accommodation.hotel")
                            .queryParam("filter", String.format(Locale.ROOT, "circle:%f,%f,20000", center[1], center[0]))
                            .queryParam("limit", 40)
                            .queryParam("apiKey", geoKey)
                            .build())
                    .retrieve()
                    .body(MAP);
        } catch (Exception e) {
            notes.add("Could not load hotels: " + describe(e));
            return List.of();
        }

        List<HotelOption> found = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        List<Map<String, Object>> features = (List<Map<String, Object>>) body.get("features");
        if (features == null) {
            return List.of();
        }
        for (Map<String, Object> f : features) {
            Map<String, Object> props = (Map<String, Object>) f.get("properties");
            Object name = props.get("name");
            if (name == null || name.toString().isBlank() || !seen.add(name.toString().toLowerCase())
                    || !(props.get("lat") instanceof Number lat) || !(props.get("lon") instanceof Number lon)) {
                continue;
            }
            Map<String, Object> raw = rawTags(props);
            int stars = stars(raw, name.toString());
            double price = hotelPrice(stars);

            List<String> confirmed = new ArrayList<>();
            List<String> unconfirmed = new ArrayList<>();
            for (String a : wanted) {
                boolean ok = switch (a) {
                    case "pool" -> has(props, raw, "swimming_pool", "pool");
                    case "wifi" -> has(props, raw, "internet_access", "wifi");
                    case "breakfast" -> has(props, raw, "breakfast");
                    default -> false;
                };
                (ok ? confirmed : unconfirmed).add(a);
            }

            double dist = km(lat.doubleValue(), lon.doubleValue(), cLat, cLon);

            // five parts, 100 points in total
            int budgetPts = max == null ? 20 : price <= max ? (int) Math.round(14 + 6 * (1 - price / max)) : 0;
            int amenityPts = wanted.isEmpty() ? 25
                    : (int) Math.round(25.0 * (confirmed.size() + 0.4 * unconfirmed.size()) / wanted.size());
            int locationPts = near ? (int) Math.round(25 * Math.max(0, 1 - dist / 25)) : 15;
            int qualityPts = stars > 0 ? stars * 4 : 12;
            boolean calm = CALM_NAME.matcher(name.toString()).find();
                        int stylePts = switch (pace) {
                case "rest" -> calm ? 10 : 5;
                case "explore" -> dist <= 5 ? 10 : dist <= 10 ? 8 : 4;
                default -> 7;
            };

            List<String> breakdown = List.of(
                    "Budget: " + budgetPts + " of 20",
                    "Amenities you asked for: " + amenityPts + " of 25",
                    "Location: " + locationPts + " of 25",
                    "Quality (stars): " + qualityPts + " of 20",
                    "Matches your pace: " + stylePts + " of 10");

            List<String> reasons = new ArrayList<>();
            reasons.add(String.format(Locale.ROOT, "About %.1f km from the main attractions", dist));
            if (calm && pace.equals("rest")) {
                reasons.add("Looks like a calm place, good for resting");
            }
            if (!confirmed.isEmpty()) {
                reasons.add("Confirmed: " + String.join(", ", confirmed));
            }
            if (!unconfirmed.isEmpty()) {
                reasons.add("Not confirmed in map data: " + String.join(", ", unconfirmed));
            }

            found.add(new HotelOption(name.toString(), stars, price, Math.round(dist * 10) / 10.0,
                    budgetPts + amenityPts + locationPts + qualityPts + stylePts, confirmed, unconfirmed, reasons,
                    lat.doubleValue(), lon.doubleValue(), breakdown));
        }

        List<HotelOption> pool = max == null ? found
                : found.stream().filter(h -> h.estimatedPricePerNight() <= max).toList();
        if (pool.isEmpty() && !found.isEmpty()) {
            notes.add("No hotel is estimated to be within your budget, so the cheapest options are shown.");
            pool = found.stream().sorted(Comparator.comparingDouble(HotelOption::estimatedPricePerNight)).limit(3).toList();
        }
        return pool.stream().sorted(Comparator.comparingInt(HotelOption::fitScore).reversed()).limit(5).toList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> rawTags(Map<String, Object> props) {
        if (props.get("datasource") instanceof Map<?, ?> source && source.get("raw") instanceof Map<?, ?> raw) {
            return (Map<String, Object>) raw;
        }
        return Map.of();
    }

    private boolean has(Map<String, Object> props, Map<String, Object> raw, String... keys) {
        for (String k : keys) {
            if (props.get("facilities") instanceof Map<?, ?> fac && Boolean.TRUE.equals(fac.get(k))) {
                return true;
            }
            Object v = raw.get(k);
            if (v != null && !"no".equalsIgnoreCase(v.toString())) {
                return true;
            }
        }
        return false;
    }

    private int stars(Map<String, Object> raw, String name) {
        Object value = raw.get("stars");
        if (value != null) {
            for (char c : value.toString().toCharArray()) {
                if (Character.isDigit(c)) {
                    return Math.min(5, c - '0');
                }
            }
        }
        Matcher m = STAR_NAME.matcher(name);
        return m.find() ? Math.min(5, Integer.parseInt(m.group(1))) : 0;
    }

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

    // ---------- Weather ----------

    @SuppressWarnings("unchecked")
    private List<DayWeather> weather(double lat, double lon, LocalDate start, LocalDate end, List<String> notes) {
        boolean forecast = !start.isBefore(LocalDate.now()) && !end.isAfter(LocalDate.now().plusDays(15));
        try {
            String url = forecast
                    ? String.format(Locale.ROOT, "https://api.open-meteo.com/v1/forecast?latitude=%f&longitude=%f&start_date=%s&end_date=%s"
                            + "&daily=temperature_2m_max,precipitation_sum,precipitation_probability_max&timezone=auto", lat, lon, start, end)
                    : String.format(Locale.ROOT, "https://archive-api.open-meteo.com/v1/archive?latitude=%f&longitude=%f&start_date=%s&end_date=%s"
                            + "&daily=temperature_2m_max,precipitation_sum&timezone=auto", lat, lon, start.minusYears(1), end.minusYears(1));
            Map<String, Object> w = http.get().uri(URI.create(url)).retrieve().body(MAP);
            Map<String, Object> daily = (Map<String, Object>) w.get("daily");
            List<Number> highs = (List<Number>) daily.get("temperature_2m_max");
            List<Number> rain = (List<Number>) daily.get("precipitation_sum");
            List<Number> chance = (List<Number>) daily.get("precipitation_probability_max");
            String source = forecast ? "forecast" : "last year's records for these dates";

            List<DayWeather> out = new ArrayList<>();
            for (int i = 0; i < highs.size(); i++) {
                double high = highs.get(i) == null ? 30 : highs.get(i).doubleValue();
                double mm = rain.get(i) == null ? 0 : rain.get(i).doubleValue();
                Integer pct = chance != null && chance.get(i) != null ? chance.get(i).intValue() : null;
                boolean rainy = mm >= 2 || (pct != null && pct >= 60);
                out.add(new DayWeather(start.plusDays(i), high, mm, pct, rainy, high >= 34, source));
            }
            if (!forecast) {
                notes.add("Your dates are beyond the 16-day forecast, so weather is based on last year's records.");
            }
            return out;
        } catch (Exception e) {
            notes.add("Weather data was unavailable, so weather rules were skipped.");
            return List.of();
        }
    }

    // ---------- Crowd (estimated) and choosing places ----------

    private int crowd(Spot s, LocalDate date, int slot) {
        double base = s.notable() ? 60 : 38;
        double time = switch (slot) {
            case 0 -> 0.65;
            case 1 -> 1.0;
            default -> s.type().contains("beach") ? 1.1 : 0.85;
        };
        boolean weekend = date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
        return (int) Math.min(100, base * time * (weekend ? 1.25 : 1.0));
    }

    private String crowdLabel(int score) {
        return score < 35 ? "Low" : score < 60 ? "Moderate" : "High";
    }

    private boolean matches(Spot s, List<String> interests) {
        String type = s.type().toLowerCase();
        for (String i : interests) {
            boolean hit = switch (i) {
                case "outdoor" -> !s.indoor();
                case "indoor" -> s.indoor() && !type.contains("shopping") && !type.contains("marketplace");
                case "shopping" -> type.contains("shopping") || type.contains("marketplace");
                case "culture" -> type.contains("museum") || type.contains("culture") || type.contains("gallery")
                        || type.contains("worship") || type.contains("temple") || type.contains("church");
                default -> false;
            };
            if (hit) {
                return true;
            }
        }
        return false;
    }

    private Spot pick(List<Spot> primary, List<Spot> fallback, Set<String> used, LocalDate date, int slot,
                      double[] prev, List<String> interests) {
        Spot best = best(primary, used, date, slot, prev, interests);
        return best != null ? best : best(fallback, used, date, slot, prev, interests);
    }

    private Spot best(List<Spot> list, Set<String> used, LocalDate date, int slot, double[] prev, List<String> interests) {
        return list.stream()
                .filter(s -> !used.contains(s.name()))
                .min(Comparator.comparingDouble(s -> crowd(s, date, slot) + km(prev[0], prev[1], s.lat(), s.lon()) * 3
                        - (matches(s, interests) ? 25 : 0)))
                .orElse(null);
    }

    private String why(Spot s, int slot, boolean rainy, boolean hotAfternoon, int crowdScore, boolean liked) {
        String reason;
        if (rainy && s.indoor()) {
            reason = "Indoor because rain is expected";
        } else if (hotAfternoon && s.indoor()) {
            reason = "Indoor to avoid the afternoon heat";
        } else if (liked) {
            reason = "Matches your interests";
        } else if (slot == 0 && !s.indoor()) {
            reason = "Outdoor early, when it is cooler and quieter";
        } else if (slot == 2 && !s.indoor()) {
            reason = "Outdoor in the evening, when the heat eases";
        } else {
            reason = "Good fit for this time of day";
        }
        return reason + "; estimated crowd: " + crowdLabel(crowdScore).toLowerCase();
    }

    // ---------- Traffic (TomTom, with a time-of-day estimate as backup) ----------

    @SuppressWarnings("unchecked")
    private Leg traffic(double[] from, Spot to, LocalDate date, String time) {
        String loc = String.format(Locale.ROOT, "%f,%f:%f,%f", from[0], from[1], to.lat(), to.lon());
        String base = "https://api.tomtom.com/routing/1/calculateRoute/" + loc
                + "/json?key=" + tomtomKey + "&traffic=true&travelMode=car&computeTravelTimeFor=all";
        Map<String, Object> r;
        String source = "TomTom (predicted for " + time + ")";
        try {
            r = http.get().uri(URI.create(base + "&departAt=" + date + "T" + time + ":00%2B05:30")).retrieve().body(MAP);
        } catch (Exception first) {
            try {
                r = http.get().uri(URI.create(base)).retrieve().body(MAP);
                source = "TomTom (current traffic)";
            } catch (Exception second) {
                return estimate(from, to, "estimated by time of day (TomTom error: " + describe(second) + ")", time);
            }
        }
        List<Map<String, Object>> routes = (List<Map<String, Object>>) r.get("routes");
        Map<String, Object> sum = (Map<String, Object>) routes.get(0).get("summary");
        double meters = ((Number) sum.get("lengthInMeters")).doubleValue();
        int seconds = ((Number) sum.get("travelTimeInSeconds")).intValue();
        int delay = sum.get("trafficDelayInSeconds") instanceof Number n ? n.intValue() : 0;
        int free = sum.get("noTrafficTravelTimeInSeconds") instanceof Number n ? n.intValue() : Math.max(1, seconds - delay);
        double ratio = (double) delay / Math.max(1, free);
        String level = ratio < 0.10 ? "Light" : ratio < 0.30 ? "Moderate" : "Heavy";
        return new Leg(to.name(), round(meters / 1000), Math.round(seconds / 60f), Math.round(delay / 60f), level, source);
    }

    // rush hours: 8-10 busy, 17-20 busiest
    private Leg estimate(double[] from, Spot to, String source, String time) {
        double km = round(km(from[0], from[1], to.lat(), to.lon()) * 1.4);
        int baseMinutes = (int) Math.round(km / 30 * 60);
        int hour = Integer.parseInt(time.substring(0, 2));
        String level = hour >= 17 && hour < 20 ? "Heavy" : hour >= 8 && hour < 10 ? "Moderate" : "Light";
        int delay = switch (level) {
            case "Heavy" -> (int) Math.round(baseMinutes * 0.35);
            case "Moderate" -> (int) Math.round(baseMinutes * 0.15);
            default -> 0;
        };
        return new Leg(to.name(), km, baseMinutes + delay, delay, level, source);
    }

    // error text with API keys removed
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

    private double km(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private double round(double v) {
        return Math.round(v * 10) / 10.0;
    }
}