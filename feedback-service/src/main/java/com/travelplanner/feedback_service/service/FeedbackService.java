package com.travelplanner.feedback_service.service;

import com.travelplanner.feedback_service.model.Feedback;
import com.travelplanner.feedback_service.repository.FeedbackRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class FeedbackService {

    private final FeedbackRepository repository;

    public FeedbackService(FeedbackRepository repository) {
        this.repository = repository;
    }

    public Feedback create(Feedback feedback) {
        feedback.setId(null);
        feedback.setCreatedAt(LocalDateTime.now());
        return repository.save(feedback);
    }

    public List<Feedback> list(Long tripId) {
        if (tripId != null) {
            return repository.findByTripId(tripId);
        }
        return repository.findAll();
    }

    public void delete(Long id) {
        Feedback existing = repository.findById(id)
                .orElseThrow(() -> new FeedbackNotFoundException(id));
        repository.delete(existing);
    }
}