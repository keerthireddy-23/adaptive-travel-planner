package com.travelplanner.stay_dining_service.repository;

import com.travelplanner.stay_dining_service.model.Place;
import com.travelplanner.stay_dining_service.model.PlaceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlaceRepository extends JpaRepository<Place, Long> {

    List<Place> findByDestinationIgnoreCase(String destination);

    List<Place> findByType(PlaceType type);

    List<Place> findByDestinationIgnoreCaseAndType(String destination, PlaceType type);
}