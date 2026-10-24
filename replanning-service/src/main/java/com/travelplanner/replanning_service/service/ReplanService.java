package com.travelplanner.replanning_service.service;

import com.travelplanner.replanning_service.model.FeedbackDto;
import com.travelplanner.replanning_service.model.ReplanResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class ReplanService {

    // a place whose average rating is at or below this is avoided
    private static final double LOW_RATING_AVERAGE = 2.5;

    private final RestClient plannerClient;
    private final RestClient feedbackClient;

    public ReplanService(@Value("${planner-service.url}") String plannerUrl,
                         @Value("${feedback-service.url}") String feedbackUrl) {
        this.plannerClient = RestClient.create(plannerUrl);
        this.feedbackClient = RestClient.create(feedbackUrl);
    }

    @SuppressWarnings("unchecked")
    public ReplanResponse replan(Long tripId) {
        // 1. Read feedback for this trip
        List<FeedbackDto> feedback = feedbackClient.get()
                .uri("/api/feedback?tripId={id}", tripId)
                .retrieve()
                .body(new ParameterizedTypeReference<List<FeedbackDto>>() {});

        // 2. Find places with a low average rating
        Map<Long, List<FeedbackDto>> byPlace = feedback.stream()
                .collect(Collectors.groupingBy(FeedbackDto::placeId));

        List<Long> excludedIds = new ArrayList<>();
        List<String> avoided = new ArrayList<>();
        byPlace.forEach((placeId, items) -> {
            double average = items.stream().mapToInt(FeedbackDto::rating).average().orElse(5);
            if (average <= LOW_RATING_AVERAGE) {
                excludedIds.add(placeId);
                avoided.add(items.get(0).placeName() + " (average rating " + String.format("%.1f", average) + ")");
            }
        });

        // 3. Build the plan before and after leaving those places out
        Map<String, Object> original = getPlan(tripId, List.of());
        Map<String, Object> updated = getPlan(tripId, excludedIds);

        // 4. Compare day by day
        List<String> changes = new ArrayList<>();
        List<Map<String, Object>> oldDays = (List<Map<String, Object>>) original.get("days");
        List<Map<String, Object>> newDays = (List<Map<String, Object>>) updated.get("days");
        for (int i = 0; i < Math.min(oldDays.size(), newDays.size()); i++) {
            for (String slot : List.of("stay", "lunch", "dinner")) {
                Object before = oldDays.get(i).get(slot);
                Object after = newDays.get(i).get(slot);
                if (!Objects.equals(before, after)) {
                    changes.add("Day " + (i + 1) + " " + slot + ": "
                            + (before == null ? "none" : before) + " -> "
                            + (after == null ? "none" : after));
                }
            }
        }

        String summary = excludedIds.isEmpty()
                ? "No low-rated places found, so the plan stays the same."
                : "Replanned without " + excludedIds.size() + " low-rated place(s). "
                  + changes.size() + " change(s) made.";

        return new ReplanResponse(tripId, summary, avoided, changes, updated);
    }

    private Map<String, Object> getPlan(Long tripId, List<Long> excluded) {
        String uri = "/api/plans/" + tripId;
        if (!excluded.isEmpty()) {
            uri += "?exclude=" + excluded.stream().map(String::valueOf).collect(Collectors.joining(","));
        }
        return plannerClient.get()
                .uri(uri)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
    }
}