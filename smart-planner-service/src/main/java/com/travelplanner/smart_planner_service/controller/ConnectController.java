package com.travelplanner.smart_planner_service.controller;

import com.travelplanner.smart_planner_service.model.Models.ConnectMatch;
import com.travelplanner.smart_planner_service.model.Models.ConnectProfile;
import com.travelplanner.smart_planner_service.model.Models.ConnectProfileRequest;
import com.travelplanner.smart_planner_service.model.Models.MeetRequest;
import com.travelplanner.smart_planner_service.model.Models.MeetRequestBody;
import com.travelplanner.smart_planner_service.model.Models.MeetResponseBody;
import com.travelplanner.smart_planner_service.service.ConnectService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/smart-plan/connect")
public class ConnectController {

    private final ConnectService service;

    public ConnectController(ConnectService service) {
        this.service = service;
    }

    @PostMapping
    public ConnectProfile optIn(@RequestBody ConnectProfileRequest request) {
        return service.optIn(request);
    }

    @DeleteMapping("/{id}")
    public void optOut(@PathVariable String id) {
        service.optOut(id);
    }

    @GetMapping("/{id}/matches")
    public List<ConnectMatch> matches(@PathVariable String id) {
        return service.matches(id);
    }

    @PostMapping("/{id}/requests")
    public MeetRequest send(@PathVariable String id, @RequestBody MeetRequestBody body) {
        return service.send(id, body);
    }

    @GetMapping("/{id}/requests")
    public List<MeetRequest> requests(@PathVariable String id) {
        return service.requestsFor(id);
    }

    @PostMapping("/requests/{requestId}/respond")
    public MeetRequest respond(@PathVariable String requestId, @RequestBody MeetResponseBody body) {
        return service.respond(requestId, body);
    }
}