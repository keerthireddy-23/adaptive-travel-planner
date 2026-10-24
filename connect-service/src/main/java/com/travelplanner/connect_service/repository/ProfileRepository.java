package com.travelplanner.connect_service.repository;

import com.travelplanner.connect_service.model.Profile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProfileRepository extends JpaRepository<Profile, Long> {

    Optional<Profile> findByTripId(Long tripId);

    List<Profile> findByDestinationIgnoreCaseAndTripIdNot(String destination, Long tripId);
}