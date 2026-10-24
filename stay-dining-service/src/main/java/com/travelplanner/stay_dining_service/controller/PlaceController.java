package com.travelplanner.stay_dining_service.controller;

import com.travelplanner.stay_dining_service.model.Place;
import com.travelplanner.stay_dining_service.model.PlaceType;
import com.travelplanner.stay_dining_service.service.PlaceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/places")
public class PlaceController {

    private final PlaceService service;

    public PlaceController(PlaceService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Place create(@Valid @RequestBody Place place) {
        return service.create(place);
    }

    @GetMapping
    public List<Place> search(@RequestParam(required = false) String destination,
                              @RequestParam(required = false) PlaceType type) {
        return service.search(destination, type);
    }

    @GetMapping("/{id}")
    public Place getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PutMapping("/{id}")
    public Place update(@PathVariable Long id, @Valid @RequestBody Place place) {
        return service.update(id, place);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}