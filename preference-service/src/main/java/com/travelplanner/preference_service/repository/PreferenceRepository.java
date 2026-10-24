package com.travelplanner.preference_service.repository;

import com.travelplanner.preference_service.model.Preference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PreferenceRepository extends JpaRepository<Preference, Long> {

    Optional<Preference> findByTripId(Long tripId);
}