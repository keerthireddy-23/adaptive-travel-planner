package com.travelplanner.stay_dining_service.service;

import com.travelplanner.stay_dining_service.model.Place;
import com.travelplanner.stay_dining_service.model.PlaceType;
import com.travelplanner.stay_dining_service.repository.PlaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PlaceService {

    private static final Logger log = LoggerFactory.getLogger(PlaceService.class);

    private final PlaceRepository repository;
    private final GeoapifyClient geoapify;

    public PlaceService(PlaceRepository repository, GeoapifyClient geoapify) {
        this.repository = repository;
        this.geoapify = geoapify;
    }

    public Place create(Place place) {
        return repository.save(place);
    }

    public List<Place> search(String destination, PlaceType type) {
        boolean hasDestination = destination != null && !destination.isBlank();

        // first time we see a destination, fetch real places from the API
        if (hasDestination && repository.findByDestinationIgnoreCase(destination.trim()).isEmpty()) {
            importFromApi(destination.trim());
        }

        if (hasDestination && type != null) {
            return repository.findByDestinationIgnoreCaseAndType(destination, type);
        }
        if (hasDestination) {
            return repository.findByDestinationIgnoreCase(destination);
        }
        if (type != null) {
            return repository.findByType(type);
        }
        return repository.findAll();
    }

    private void importFromApi(String destination) {
        try {
            List<Place> fetched = geoapify.fetchPlaces(destination);
            repository.saveAll(fetched);
            log.info("Imported {} places for {} from Geoapify", fetched.size(), destination);
        } catch (Exception e) {
            log.warn("Could not fetch places for {}: {}", destination, e.getMessage());
        }
    }

    public Place getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new PlaceNotFoundException(id));
    }

    public Place update(Long id, Place updated) {
        Place existing = getById(id);
        existing.setName(updated.getName());
        existing.setDestination(updated.getDestination());
        existing.setType(updated.getType());
        existing.setPrice(updated.getPrice());
        existing.setRating(updated.getRating());
        existing.setCategory(updated.getCategory());
        return repository.save(existing);
    }

    public void delete(Long id) {
        repository.delete(getById(id));
    }
}