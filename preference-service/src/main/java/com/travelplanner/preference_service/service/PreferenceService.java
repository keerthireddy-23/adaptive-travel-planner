package com.travelplanner.preference_service.service;

import com.travelplanner.preference_service.model.Preference;
import com.travelplanner.preference_service.repository.PreferenceRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PreferenceService {

    private final PreferenceRepository repository;

    public PreferenceService(PreferenceRepository repository) {
        this.repository = repository;
    }

    public List<Preference> getAll() {
        return repository.findAll();
    }

    public Preference getByTripId(Long tripId) {
        return repository.findByTripId(tripId)
                .orElseThrow(() -> new PreferenceNotFoundException(tripId));
    }

    // creates the preference if none exists for the trip, otherwise replaces it
    public Preference save(Long tripId, Preference incoming) {
        Preference pref = repository.findByTripId(tripId).orElseGet(Preference::new);
        pref.setTripId(tripId);
        pref.setPreferredCategories(incoming.getPreferredCategories());
        pref.setAvoidCategories(incoming.getAvoidCategories());
        pref.setMaxMealPrice(incoming.getMaxMealPrice());
        pref.setMinRating(incoming.getMinRating());
        return repository.save(pref);
    }

    public void delete(Long tripId) {
        repository.delete(getByTripId(tripId));
    }
}