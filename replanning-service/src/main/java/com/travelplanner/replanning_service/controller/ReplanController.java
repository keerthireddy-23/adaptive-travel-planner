package com.travelplanner.replanning_service.controller;

import com.travelplanner.replanning_service.model.ReplanResponse;
import com.travelplanner.replanning_service.service.ReplanService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/replan")
public class ReplanController {

    private final ReplanService service;

    public ReplanController(ReplanService service) {
        this.service = service;
    }

    @PostMapping("/{tripId}")
    public ReplanResponse replan(@PathVariable Long tripId) {
        return service.replan(tripId);
    }
}