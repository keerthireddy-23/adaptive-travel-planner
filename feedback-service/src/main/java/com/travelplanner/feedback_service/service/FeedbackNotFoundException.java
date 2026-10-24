package com.travelplanner.feedback_service.service;

public class FeedbackNotFoundException extends RuntimeException {

    public FeedbackNotFoundException(Long id) {
        super("Feedback not found with id: " + id);
    }
}