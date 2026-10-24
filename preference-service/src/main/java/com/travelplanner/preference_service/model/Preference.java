package com.travelplanner.preference_service.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.PositiveOrZero;

@Entity
public class Preference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private Long tripId;

    // comma separated, e.g. "Indian, cafe"
    private String preferredCategories;

    // comma separated, e.g. "seafood"
    private String avoidCategories;

    // 0 means no limit
    @PositiveOrZero(message = "Max meal price cannot be negative")
    private double maxMealPrice;

    @DecimalMin(value = "0.0", message = "Minimum rating must be between 0 and 5")
    @DecimalMax(value = "5.0", message = "Minimum rating must be between 0 and 5")
    private double minRating;

    public Preference() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTripId() {
        return tripId;
    }

    public void setTripId(Long tripId) {
        this.tripId = tripId;
    }

    public String getPreferredCategories() {
        return preferredCategories;
    }

    public void setPreferredCategories(String preferredCategories) {
        this.preferredCategories = preferredCategories;
    }

    public String getAvoidCategories() {
        return avoidCategories;
    }

    public void setAvoidCategories(String avoidCategories) {
        this.avoidCategories = avoidCategories;
    }

    public double getMaxMealPrice() {
        return maxMealPrice;
    }

    public void setMaxMealPrice(double maxMealPrice) {
        this.maxMealPrice = maxMealPrice;
    }

    public double getMinRating() {
        return minRating;
    }

    public void setMinRating(double minRating) {
        this.minRating = minRating;
    }
}