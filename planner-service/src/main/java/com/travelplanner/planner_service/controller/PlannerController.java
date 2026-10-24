package com.travelplanner.planner_service.controller;

import com.travelplanner.planner_service.model.ItineraryResponse;
import com.travelplanner.planner_service.service.PlannerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/plans")
public class PlannerController {

    private final PlannerService service;

    public PlannerController(PlannerService service) {
        this.service = service;
    }

    @GetMapping("/{tripId}")
    public ItineraryResponse getPlan(@PathVariable Long tripId,
                                     @RequestParam(required = false) List<Long> exclude) {
        Set<Long> excluded = exclude == null ? Set.of() : new HashSet<>(exclude);
        return service.buildItinerary(tripId, excluded);
    }
}