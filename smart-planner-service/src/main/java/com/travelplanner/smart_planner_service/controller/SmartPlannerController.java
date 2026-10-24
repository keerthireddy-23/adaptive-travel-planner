package com.travelplanner.smart_planner_service.controller;

import com.travelplanner.smart_planner_service.model.Models.Booking;
import com.travelplanner.smart_planner_service.model.Models.BookingRequest;
import com.travelplanner.smart_planner_service.model.Models.FoodPlan;
import com.travelplanner.smart_planner_service.model.Models.FoodRequest;
import com.travelplanner.smart_planner_service.model.Models.Journey;
import com.travelplanner.smart_planner_service.model.Models.JourneyRequest;
import com.travelplanner.smart_planner_service.model.Models.Review;
import com.travelplanner.smart_planner_service.model.Models.ReviewRequest;
import com.travelplanner.smart_planner_service.model.Models.ReviewSummary;
import com.travelplanner.smart_planner_service.model.Models.SmartPlan;
import com.travelplanner.smart_planner_service.model.Models.SmartPlanRequest;
import com.travelplanner.smart_planner_service.service.BookingService;
import com.travelplanner.smart_planner_service.service.FoodService;
import com.travelplanner.smart_planner_service.service.JourneyService;
import com.travelplanner.smart_planner_service.service.ReviewService;
import com.travelplanner.smart_planner_service.service.SmartPlannerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/smart-plan")
public class SmartPlannerController {

    private final SmartPlannerService service;
    private final JourneyService journeyService;
    private final BookingService bookingService;
    private final FoodService foodService;
    private final ReviewService reviewService;

    public SmartPlannerController(SmartPlannerService service, JourneyService journeyService,
                                  BookingService bookingService, FoodService foodService,
                                  ReviewService reviewService) {
        this.service = service;
        this.journeyService = journeyService;
        this.bookingService = bookingService;
        this.foodService = foodService;
        this.reviewService = reviewService;
    }

    @PostMapping
    public SmartPlan plan(@RequestBody SmartPlanRequest request) {
        return service.plan(request);
    }

    @PostMapping("/journey")
    public Journey journey(@RequestBody JourneyRequest request) {
        return journeyService.plan(request);
    }

    @PostMapping("/bookings")
    public Booking book(@RequestBody BookingRequest request) {
        return bookingService.book(request);
    }

    @GetMapping("/bookings")
    public List<Booking> bookings() {
        return bookingService.all();
    }

    @PostMapping("/food")
    public FoodPlan food(@RequestBody FoodRequest request) {
        return foodService.plan(request);
    }

    @PostMapping("/reviews")
    public Review addReview(@RequestBody ReviewRequest request) {
        return reviewService.add(request);
    }

    @GetMapping("/reviews")
    public ReviewSummary reviews(@RequestParam String placeName,
                                 @RequestParam(required = false) String destination) {
        return reviewService.summary(placeName, destination);
    }
}