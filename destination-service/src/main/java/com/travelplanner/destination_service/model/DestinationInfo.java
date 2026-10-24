package com.travelplanner.destination_service.model;

import java.util.List;

public record DestinationInfo(
        String destination,
        String resolvedName,
        double latitude,
        double longitude,
        String climateBasis,
        double avgHighC,
        double avgLowC,
        double rainMm,
        long rainyDays,
        int daysChecked,
        String advice,
        List<String> attractions) {
}