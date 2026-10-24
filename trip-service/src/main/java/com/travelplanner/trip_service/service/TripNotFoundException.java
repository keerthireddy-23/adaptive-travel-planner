package com.travelplanner.trip_service.service;

public class TripNotFoundException extends RuntimeException {

    public TripNotFoundException(Long id) {
        super("Trip not found with id: " + id);
    }
}