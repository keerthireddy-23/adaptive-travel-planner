package com.travelplanner.trip_service.service;

import com.travelplanner.trip_service.model.Trip;
import com.travelplanner.trip_service.repository.TripRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TripService {

    private final TripRepository repository;

    public TripService(TripRepository repository) {
        this.repository = repository;
    }

    public Trip create(Trip trip) {
        validateDates(trip);
        return repository.save(trip);
    }

    public List<Trip> getAll() {
        return repository.findAll();
    }

    public List<Trip> getByDestination(String destination) {
        return repository.findByDestinationIgnoreCase(destination);
    }

    public Trip getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new TripNotFoundException(id));
    }

    public Trip update(Long id, Trip updated) {
        validateDates(updated);
        Trip existing = getById(id);
        existing.setDestination(updated.getDestination());
        existing.setStartDate(updated.getStartDate());
        existing.setEndDate(updated.getEndDate());
        existing.setBudget(updated.getBudget());
        existing.setTravelers(updated.getTravelers());
        existing.setSoloTraveler(updated.isSoloTraveler());
        return repository.save(existing);
    }

    public void delete(Long id) {
        Trip existing = getById(id);
        repository.delete(existing);
    }

    private void validateDates(Trip trip) {
        if (trip.getStartDate() != null && trip.getEndDate() != null
                && trip.getEndDate().isBefore(trip.getStartDate())) {
            throw new IllegalArgumentException("End date must not be before start date");
        }
    }
}