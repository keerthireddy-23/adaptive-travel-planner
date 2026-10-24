package com.travelplanner.feedback_service.repository;

import com.travelplanner.feedback_service.model.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    List<Feedback> findByTripId(Long tripId);
}