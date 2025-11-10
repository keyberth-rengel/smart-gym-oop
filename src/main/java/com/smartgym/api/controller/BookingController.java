package com.smartgym.api.controller;

import com.smartgym.api.common.ApiResponse;
import com.smartgym.api.dto.BookingCreateRequest;
import com.smartgym.api.dto.BookingResponse;
import com.smartgym.model.Booking;
import com.smartgym.service.SmartGymService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@io.swagger.v3.oas.annotations.tags.Tag(name = "Bookings")
@RestController
@RequestMapping("/api/v1")
@Validated
public class BookingController {

    private final SmartGymService service;

    public BookingController(SmartGymService service) { this.service = service; }

    @Operation(summary = "Create a booking")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201", description = "Created",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409", description = "Conflict (duplicate or trainer busy)",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "422", description = "Unprocessable (invalid domain state)",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @PostMapping("/bookings")
    public ResponseEntity<ApiResponse<?>> create(@Valid @RequestBody BookingCreateRequest req, HttpServletRequest http) {
        var date = LocalDate.parse(req.date());
        var time = LocalTime.parse(req.time());
        Booking b = (req.note() == null || req.note().isBlank())
                ? service.createBooking(req.customerEmail(), req.trainerEmail(), date, time)
                : service.createBooking(req.customerEmail(), req.trainerEmail(), date, time, req.note());

        var resp = new BookingResponse(
                b.getId(),
                b.getCustomerEmail(),
                b.getTrainerEmail(),
                b.getSchedule().getDate().toString(),
                b.getSchedule().getTime().toString(),
                b.getNote()
        );
        return ResponseEntity.status(201).body(
                ApiResponse.ok(resp, "Booking created successfully.", java.time.Instant.now().toString(), http.getRequestURI())
        );
    }

    @Operation(summary = "List all bookings")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "OK",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @GetMapping("/bookings")
    public ResponseEntity<ApiResponse<List<BookingResponse>>> listAll(HttpServletRequest http) {
        var list = service.listBookings().stream().map(this::toResponse).toList();
        return ResponseEntity.ok(
                ApiResponse.ok(list, "All bookings retrieved successfully.", java.time.Instant.now().toString(), http.getRequestURI())
        );
    }

    @Operation(summary = "List trainer bookings by date")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "OK",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @GetMapping("/trainers/{email}/bookings")
    public ResponseEntity<ApiResponse<List<BookingResponse>>> listByTrainerAndDate(
            @PathVariable String email,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            HttpServletRequest http) {
        var list = service.listTrainerBookings(email, date).stream().map(this::toResponse).toList();
        return ResponseEntity.ok(
                ApiResponse.ok(list, "Trainer bookings for the given date retrieved successfully.", java.time.Instant.now().toString(), http.getRequestURI())
        );
    }

    @Operation(summary = "Cancel booking by id")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "No content")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "Not found",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @DeleteMapping("/bookings/{id}")
    public ResponseEntity<ApiResponse<?>> delete(@PathVariable int id,
                                                 jakarta.servlet.http.HttpServletRequest http) {
        service.cancelBooking(id);
        var payload = java.util.Map.of("id", id, "status", "CANCELLED");
        return org.springframework.http.ResponseEntity.ok(
                com.smartgym.api.common.ApiResponse.ok(
                        payload, "Booking cancelled successfully.", java.time.Instant.now().toString(), http.getRequestURI()
                )
        );
    }

    private BookingResponse toResponse(Booking b) {
        return new BookingResponse(
                b.getId(),
                b.getCustomerEmail(),
                b.getTrainerEmail(),
                b.getSchedule().getDate().toString(),
                b.getSchedule().getTime().toString(),
                b.getNote()
        );
    }
}