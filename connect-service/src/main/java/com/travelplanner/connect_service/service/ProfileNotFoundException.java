package com.travelplanner.connect_service.service;

public class ProfileNotFoundException extends RuntimeException {

    public ProfileNotFoundException(Long tripId) {
        super("This trip has not opted in to traveler connect: " + tripId);
    }
}