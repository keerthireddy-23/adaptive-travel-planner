package com.travelplanner.stay_dining_service.service;

public class PlaceNotFoundException extends RuntimeException {

    public PlaceNotFoundException(Long id) {
        super("Place not found with id: " + id);
    }
}