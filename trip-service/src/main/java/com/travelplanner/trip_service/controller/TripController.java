package com.travelplanner.trip_service.controller;

import com.travelplanner.trip_service.model.Trip;
import com.travelplanner.trip_service.service.TripService;
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
@RequestMapping("/api/trips")
public class TripController {

    private final TripService service;

    public TripController(TripService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Trip create(@Valid @RequestBody Trip trip) {
        return service.create(trip);
    }

    @GetMapping
    public List<Trip> getAll(@RequestParam(required = false) String destination) {
        if (destination != null && !destination.isBlank()) {
            return service.getByDestination(destination);
        }
        return service.getAll();
    }

    @GetMapping("/{id}")
    public Trip getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PutMapping("/{id}")
    public Trip update(@PathVariable Long id, @Valid @RequestBody Trip trip) {
        return service.update(id, trip);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}