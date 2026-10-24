package com.travelplanner.destination_service.service;

public class DestinationNotFoundException extends RuntimeException {

    public DestinationNotFoundException(String name) {
        super("Could not find a place called: " + name);
    }
}