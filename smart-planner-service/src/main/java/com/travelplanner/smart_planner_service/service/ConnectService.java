package com.travelplanner.smart_planner_service.service;

import com.travelplanner.smart_planner_service.model.Models.ConnectMatch;
import com.travelplanner.smart_planner_service.model.Models.ConnectProfile;
import com.travelplanner.smart_planner_service.model.Models.ConnectProfileRequest;
import com.travelplanner.smart_planner_service.model.Models.MeetRequest;
import com.travelplanner.smart_planner_service.model.Models.MeetRequestBody;
import com.travelplanner.smart_planner_service.model.Models.MeetResponseBody;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ConnectService {

    private final Map<String, ConnectProfile> profiles = new ConcurrentHashMap<>();
    private final Map<String, MeetRequest> requests = new ConcurrentHashMap<>();

    public ConnectProfile optIn(ConnectProfileRequest r) {
        if (r.displayName() == null || r.displayName().isBlank() || r.displayName().length() > 30) {
            throw new IllegalArgumentException("Display name is required (30 characters at most).");
        }
        if (r.destination() == null || r.destination().isBlank() || r.startDate() == null
                || r.endDate() == null || r.endDate().isBefore(r.startDate())) {
            throw new IllegalArgumentException("Destination and valid start and end dates are required.");
        }
        List<String> interests = r.interests() == null ? List.of() : r.interests().stream()
                .map(i -> i.trim().toLowerCase()).filter(i -> !i.isEmpty()).limit(8).toList();
        String pace = r.pace() == null ? "explore" : r.pace().trim().toLowerCase();

        ConnectProfile profile = new ConnectProfile(UUID.randomUUID().toString().substring(0, 8),
                r.displayName().trim(), r.destination().trim(), r.startDate(), r.endDate(), interests, pace);
                List<String> oldIds = profiles.values().stream()
                .filter(p -> p.displayName().equalsIgnoreCase(profile.displayName())
                        && p.destination().equalsIgnoreCase(profile.destination()))
                .map(ConnectProfile::id)
                .toList();
        oldIds.forEach(profiles::remove);
        requests.values().removeIf(q -> oldIds.contains(q.fromId()) || oldIds.contains(q.toId()));
        profiles.put(profile.id(), profile);
        return profile;
    }

    public void optOut(String id) {
        if (profiles.remove(id) == null) {
            throw new IllegalArgumentException("Profile not found.");
        }
        requests.values().removeIf(q -> q.fromId().equals(id) || q.toId().equals(id));
    }

    public List<ConnectMatch> matches(String id) {
        ConnectProfile me = get(id);
        return profiles.values().stream()
                .filter(o -> !o.id().equals(id) && o.destination().equalsIgnoreCase(me.destination()))
                .filter(o -> !o.startDate().isAfter(me.endDate()) && !o.endDate().isBefore(me.startDate()))
                .map(o -> {
                    List<String> shared = o.interests().stream().filter(me.interests()::contains).toList();
                    LocalDate from = o.startDate().isAfter(me.startDate()) ? o.startDate() : me.startDate();
                    LocalDate to = o.endDate().isBefore(me.endDate()) ? o.endDate() : me.endDate();
                    boolean samePace = o.pace().equals(me.pace());
                    int score = Math.min(100, 40 + 15 * Math.min(3, shared.size()) + (samePace ? 15 : 0));
                    return new ConnectMatch(o.id(), o.displayName(), from, to, shared, samePace, score,
                            o.pace(), o.interests());
                })
                .sorted(Comparator.comparingInt(ConnectMatch::compatibility).reversed())
                .toList();
    }

    public MeetRequest send(String fromId, MeetRequestBody body) {
        ConnectProfile from = get(fromId);
        if (body.toId() == null || body.toId().equals(fromId)) {
            throw new IllegalArgumentException("Choose another traveler to meet.");
        }
        ConnectProfile to = get(body.toId());
        boolean duplicate = requests.values().stream()
                .anyMatch(q -> q.fromId().equals(fromId) && q.toId().equals(to.id()) && q.status().equals("PENDING"));
        if (duplicate) {
            throw new IllegalArgumentException("You already sent a request to " + to.displayName() + ".");
        }
        String message = body.message() == null ? "" : body.message().trim();
        if (message.length() > 200) {
            message = message.substring(0, 200);
        }
        MeetRequest req = new MeetRequest(UUID.randomUUID().toString().substring(0, 8), fromId, from.displayName(),
                to.id(), to.displayName(), message, "PENDING");
        requests.put(req.id(), req);
        return req;
    }

    public List<MeetRequest> requestsFor(String id) {
        get(id);
        return requests.values().stream()
                .filter(q -> q.fromId().equals(id) || q.toId().equals(id))
                .toList();
    }

    public MeetRequest respond(String requestId, MeetResponseBody body) {
        MeetRequest q = requests.get(requestId);
        if (q == null) {
            throw new IllegalArgumentException("Request not found.");
        }
        if (body.responderId() == null || !body.responderId().equals(q.toId())) {
            throw new IllegalArgumentException("Only the traveler who received the request can answer it.");
        }
        String decision = body.decision() == null ? "" : body.decision().trim().toLowerCase();
        if (!decision.equals("accept") && !decision.equals("decline")) {
            throw new IllegalArgumentException("Decision must be accept or decline.");
        }
        MeetRequest updated = new MeetRequest(q.id(), q.fromId(), q.fromName(), q.toId(), q.toName(),
                q.message(), decision.equals("accept") ? "ACCEPTED" : "DECLINED");
        requests.put(requestId, updated);
        return updated;
    }

    private ConnectProfile get(String id) {
        ConnectProfile p = profiles.get(id);
        if (p == null) {
            throw new IllegalArgumentException("Profile not found. Opt in first.");
        }
        return p;
    }
}