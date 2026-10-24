package com.travelplanner.smart_planner_service.service;

import com.travelplanner.smart_planner_service.model.Models.Booking;
import com.travelplanner.smart_planner_service.model.Models.BookingRequest;
import org.springframework.stereotype.Service;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class BookingService {

    private final List<Booking> bookings = new CopyOnWriteArrayList<>();

    public Booking book(BookingRequest r) {
        if (r.hotelName() == null || r.hotelName().isBlank()
                || r.guestName() == null || r.guestName().isBlank()
                || r.checkIn() == null || r.checkOut() == null) {
            throw new IllegalArgumentException("Hotel, guest name, check-in and check-out are required.");
        }
        if (!r.checkOut().isAfter(r.checkIn())) {
            throw new IllegalArgumentException("Check-out must be after check-in.");
        }
        int rooms = r.rooms() == null ? 1 : r.rooms();
        if (rooms < 1 || rooms > 5) {
            throw new IllegalArgumentException("Rooms must be between 1 and 5.");
        }
        int nights = (int) ChronoUnit.DAYS.between(r.checkIn(), r.checkOut());
        double price = r.pricePerNight() == null ? 2500 : r.pricePerNight();
        String ref = "TP-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        Booking booking = new Booking(ref, r.hotelName().trim(), r.guestName().trim(), r.checkIn(), r.checkOut(),
                nights, rooms, price * nights * rooms, "CONFIRMED (demo)",
                "Demo booking only. No payment was taken and the hotel was not contacted.");
        bookings.add(booking);
        return booking;
    }

    public List<Booking> all() {
        return List.copyOf(bookings);
    }
}