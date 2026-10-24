package com.travelplanner.preference_service.controller;

import com.travelplanner.preference_service.model.Preference;
import com.travelplanner.preference_service.service.PreferenceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/preferences")
public class PreferenceController {

    private final PreferenceService service;

    public PreferenceController(PreferenceService service) {
        this.service = service;
    }

    @GetMapping
    public List<Preference> getAll() {
        return service.getAll();
    }

    @GetMapping("/trip/{tripId}")
    public Preference getByTripId(@PathVariable Long tripId) {
        return service.getByTripId(tripId);
    }

    @PutMapping("/trip/{tripId}")
    public Preference save(@PathVariable Long tripId, @Valid @RequestBody Preference preference) {
        return service.save(tripId, preference);
    }

    @DeleteMapping("/trip/{tripId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long tripId) {
        service.delete(tripId);
    }
}