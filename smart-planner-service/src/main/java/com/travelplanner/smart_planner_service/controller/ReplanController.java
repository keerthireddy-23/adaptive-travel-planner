package com.travelplanner.smart_planner_service.controller;

import com.travelplanner.smart_planner_service.model.Models.ReplanRequest;
import com.travelplanner.smart_planner_service.model.Models.ReplanResult;
import com.travelplanner.smart_planner_service.service.ReplanService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/smart-plan/replan")
public class ReplanController {

    private final ReplanService service;

    public ReplanController(ReplanService service) {
        this.service = service;
    }

    @PostMapping
    public ReplanResult replan(@RequestBody ReplanRequest request) {
        return service.replan(request);
    }
}