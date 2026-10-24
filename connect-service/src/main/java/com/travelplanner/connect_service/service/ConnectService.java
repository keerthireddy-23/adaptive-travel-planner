package com.travelplanner.connect_service.service;

import com.travelplanner.connect_service.model.MatchDto;
import com.travelplanner.connect_service.model.Profile;
import com.travelplanner.connect_service.model.ProfileRequest;
import com.travelplanner.connect_service.model.TripDto;
import com.travelplanner.connect_service.repository.ProfileRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ConnectService {

    private final ProfileRepository repository;
    private final RestClient tripClient;

    public ConnectService(ProfileRepository repository, @Value("${trip-service.url}") String tripUrl) {
        this.repository = repository;
        this.tripClient = RestClient.create(tripUrl);
    }

    public Profile optIn(Long tripId, ProfileRequest request) {
        TripDto trip = tripClient.get()
                .uri("/api/trips/{id}", tripId)
                .retrieve()
                .body(TripDto.class);

        if (!trip.soloTraveler()) {
            throw new IllegalArgumentException("Only trips marked as solo can join traveler connect.");
        }

        Profile profile = repository.findByTripId(tripId).orElseGet(Profile::new);
        profile.setTripId(tripId);
        profile.setDisplayName(request.displayName().trim());
        profile.setInterests(request.interests());
        profile.setDestination(trip.destination());
        profile.setStartDate(trip.startDate());
        profile.setEndDate(trip.endDate());
        return repository.save(profile);
    }

    public void optOut(Long tripId) {
        repository.delete(repository.findByTripId(tripId)
                .orElseThrow(() -> new ProfileNotFoundException(tripId)));
    }

    public List<MatchDto> matches(Long tripId) {
        Profile me = repository.findByTripId(tripId)
                .orElseThrow(() -> new ProfileNotFoundException(tripId));
        Set<String> mine = parse(me.getInterests());

        return repository.findByDestinationIgnoreCaseAndTripIdNot(me.getDestination(), tripId).stream()
                .filter(o -> !o.getStartDate().isAfter(me.getEndDate()) && !o.getEndDate().isBefore(me.getStartDate()))
                .map(o -> {
                    List<String> shared = parse(o.getInterests()).stream().filter(mine::contains).sorted().toList();
                    LocalDate from = o.getStartDate().isAfter(me.getStartDate()) ? o.getStartDate() : me.getStartDate();
                    LocalDate to = o.getEndDate().isBefore(me.getEndDate()) ? o.getEndDate() : me.getEndDate();
                    return new MatchDto(o.getDisplayName(), o.getTripId(), o.getInterests(), shared, from, to);
                })
                .sorted(Comparator.comparingInt((MatchDto m) -> m.sharedInterests().size()).reversed())
                .toList();
    }

    private Set<String> parse(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(csv.split(","))
                .map(s -> s.trim().toLowerCase())
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }
}