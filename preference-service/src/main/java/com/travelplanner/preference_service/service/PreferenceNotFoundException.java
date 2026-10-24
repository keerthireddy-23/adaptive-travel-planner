package com.travelplanner.preference_service.service;

public class PreferenceNotFoundException extends RuntimeException {

    public PreferenceNotFoundException(Long tripId) {
        super("No preferences saved for trip id: " + tripId);
    }
}