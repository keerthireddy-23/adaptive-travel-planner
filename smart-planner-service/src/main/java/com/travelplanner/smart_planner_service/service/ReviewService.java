package com.travelplanner.smart_planner_service.service;

import com.travelplanner.smart_planner_service.model.Models.Review;
import com.travelplanner.smart_planner_service.model.Models.ReviewRequest;
import com.travelplanner.smart_planner_service.model.Models.ReviewSummary;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class ReviewService {

    private final List<Review> reviews = new CopyOnWriteArrayList<>();

    public Review add(ReviewRequest r) {
        if (r.placeName() == null || r.placeName().isBlank()
                || r.destination() == null || r.destination().isBlank()) {
            throw new IllegalArgumentException("Place name and destination are required.");
        }
        String type = r.placeType() == null ? "" : r.placeType().trim().toLowerCase();
        if (!type.equals("hotel") && !type.equals("restaurant")) {
            throw new IllegalArgumentException("Place type must be hotel or restaurant.");
        }
        if (r.rating() == null || r.rating() < 1 || r.rating() > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5.");
        }
        String comment = r.comment() == null ? "" : r.comment().trim();
        if (comment.length() > 300) {
            comment = comment.substring(0, 300);
        }
        String author = r.author() == null || r.author().isBlank() ? "Traveler" : r.author().trim();

        Review review = new Review(type, r.placeName().trim(), r.destination().trim(), r.rating(),
                comment, author, LocalDateTime.now().toString());
        reviews.add(review);
        return review;
    }

    public ReviewSummary summary(String placeName, String destination) {
        List<Review> list = reviews.stream()
                .filter(x -> x.placeName().equalsIgnoreCase(placeName.trim())
                        && (destination == null || destination.isBlank()
                        || x.destination().equalsIgnoreCase(destination.trim())))
                .toList();
        double average = Math.round(list.stream().mapToInt(Review::rating).average().orElse(0) * 10) / 10.0;
        return new ReviewSummary(placeName.trim(), average, list.size(), list);
    }
}