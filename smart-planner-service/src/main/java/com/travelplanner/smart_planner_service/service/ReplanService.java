package com.travelplanner.smart_planner_service.service;

import com.travelplanner.smart_planner_service.model.Models.DayPlan;
import com.travelplanner.smart_planner_service.model.Models.ReplanRequest;
import com.travelplanner.smart_planner_service.model.Models.ReplanResult;
import com.travelplanner.smart_planner_service.model.Models.SmartPlan;
import com.travelplanner.smart_planner_service.model.Models.SmartPlanRequest;
import com.travelplanner.smart_planner_service.model.Models.Stop;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ReplanService {

    private final SmartPlannerService planner;

    public ReplanService(SmartPlannerService planner) {
        this.planner = planner;
    }

    public ReplanResult replan(ReplanRequest r) {
        if (r.plan() == null || r.reason() == null) {
            throw new IllegalArgumentException("A plan and a reason (weather, traffic, crowd or skip) are required.");
        }
        String reason = r.reason().trim().toLowerCase();
        SmartPlanRequest base = r.plan();
        SmartPlan before = planner.plan(base);

        List<String> avoid = new ArrayList<>(base.avoid() == null ? List.of() : base.avoid());
        boolean forceIndoor = false;
        String why;

        switch (reason) {
            case "weather" -> {
                forceIndoor = true;
                why = "bad weather, so indoor places are preferred";
            }
            case "traffic" -> {
                before.days().forEach(d -> d.stops().forEach(s -> {
                    if ("Heavy".equals(s.travel().traffic()) || s.travel().minutes() > 25) {
                        avoid.add(s.name());
                    }
                }));
                why = "traffic, so stops with heavy traffic or long drives are replaced by closer ones";
            }
            case "crowd" -> {
                before.days().forEach(d -> d.stops().forEach(s -> {
                    if (s.crowdScore() >= 40) {
                        avoid.add(s.name());
                    }
                }));
                why = "crowds, so the busiest places are replaced by quieter ones";
            }
            case "skip" -> {
                if (r.avoid() == null || r.avoid().isEmpty()) {
                    throw new IllegalArgumentException("Tell us which places to skip.");
                }
                avoid.addAll(r.avoid());
                why = "your request, so those places are skipped";
            }
            default -> throw new IllegalArgumentException("Reason must be weather, traffic, crowd or skip.");
        }

        SmartPlan after = planner.plan(new SmartPlanRequest(base.destination(), base.startDate(), base.endDate(),
                base.maxHotelPerNight(), base.amenities(), base.nearAttractions(), base.pace(), base.interests(),
                avoid, forceIndoor));

        List<String> changes = new ArrayList<>();
        for (int d = 0; d < Math.min(before.days().size(), after.days().size()); d++) {
            DayPlan a = before.days().get(d);
            DayPlan b = after.days().get(d);
            for (int i = 0; i < Math.min(a.stops().size(), b.stops().size()); i++) {
                Stop x = a.stops().get(i);
                Stop y = b.stops().get(i);
                if (!x.name().equals(y.name())) {
                    changes.add("Day " + (d + 1) + " at " + x.time() + ": " + x.name() + " -> " + y.name());
                }
            }
        }

        String summary = changes.isEmpty()
                ? "Nothing needed to change for " + why + "."
                : changes.size() + " stop(s) changed because of " + why + ".";
        return new ReplanResult(reason, summary, changes, after);
    }
}