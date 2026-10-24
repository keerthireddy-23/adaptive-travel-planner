package com.travelplanner.trip_service.repository;

import com.travelplanner.trip_service.model.Trip;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TripRepository extends JpaRepository<Trip, Long> {

    List<Trip> findByDestinationIgnoreCase(String destination);
}