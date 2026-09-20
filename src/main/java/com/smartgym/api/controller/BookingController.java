package com.smartgym.api.controller;

import com.smartgym.api.common.ApiResponse;
import com.smartgym.api.dto.AvailabilityResponse;
import com.smartgym.api.dto.BookingCreateRequest;
import com.smartgym.api.dto.BookingResponse;
import com.smartgym.model.Booking;
import com.smartgym.security.AccessGuard;
import com.smartgym.security.CurrentUser;
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
import java.time.format.DateTimeFormatter;
import java.util.List;

@io.swagger.v3.oas.annotations.tags.Tag(name = "Bookings")
@RestController
@RequestMapping("/api/v1")
@Validated
public class BookingController {

    private final SmartGymService service;
    private final AccessGuard access;
    private final CurrentUser user;

    public BookingController(SmartGymService service, AccessGuard access, CurrentUser user) {
        this.service = service;
        this.access = access;
        this.user = user;
    }

    @Operation(summary = "Create a booking",
            description = "Customer: only for their own email. Admin: any customer. Trainer: 403.")
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
        access.requireBookingFor(req.customerEmail());
                var time = LocalTime.parse(req.time());
        Booking b = (req.note() == null || req.note().isBlank())
                ? service.createBookingToday(req.customerEmail(), req.trainerEmail(), time)
                : service.createBookingToday(req.customerEmail(), req.trainerEmail(), time, req.note());

        var resp = new BookingResponse(
                b.getId(),
                b.getCustomerEmail(),
                b.getTrainerEmail(),
                b.getSchedule().getDate().toString(),
                b.getSchedule().getTime().toString(),
                b.getNote()
        );
        return ResponseEntity.status(201).body(
                ApiResponse.ok(resp, "Booking created successfully", java.time.Instant.now().toString(), http.getRequestURI())
        );
    }

    @Operation(summary = "List bookings",
            description = "Admin: all bookings. Trainer: only their own. Customer: only their own.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "OK",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @GetMapping("/bookings")
    public ResponseEntity<ApiResponse<List<BookingResponse>>> listAll(HttpServletRequest http) {
        var source = user.isAdmin() ? service.listBookings()
                : user.isTrainer() ? service.listBookingsForTrainer(user.email())
                : service.listBookingsForCustomer(user.email());
        var list = source.stream().map(this::toResponse).toList();
        return ResponseEntity.ok(
                ApiResponse.ok(list, "All bookings retrieved successfully", java.time.Instant.now().toString(), http.getRequestURI())
        );
    }

    @Operation(summary = "List trainer bookings",
            description = "Optional filters: `date` (a single day; takes precedence) or `from`/`to` (inclusive range). "
                    + "Without parameters returns all of the trainer's bookings, ordered by date and time. "
                    + "Admin: any trainer. Trainer: only their own. Customers: 403 (use /availability).")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "OK",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403", description = "Not allowed to see this trainer's bookings",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "422", description = "`from` is after `to`",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @GetMapping("/trainers/{email}/bookings")
    public ResponseEntity<ApiResponse<List<BookingResponse>>> listByTrainer(
            @PathVariable String email,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            HttpServletRequest http) {
        access.requireTrainerScope(email);
        var bookings = (date != null)
                ? service.listTrainerBookings(email, date)
                : service.listTrainerBookings(email, from, to);
        var list = bookings.stream().map(this::toResponse).toList();
        return ResponseEntity.ok(
                ApiResponse.ok(list, "Trainer bookings retrieved successfully", java.time.Instant.now().toString(), http.getRequestURI())
        );
    }

    @Operation(summary = "Trainer availability for a date",
            description = "Occupied times only (HH:mm), no personal data. Any authenticated user. `date` defaults to today.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "OK",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "422", description = "Trainer not found",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @GetMapping("/trainers/{email}/availability")
    public ResponseEntity<ApiResponse<AvailabilityResponse>> availability(
            @PathVariable String email,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            HttpServletRequest http) {
        LocalDate day = (date != null) ? date : LocalDate.now();
        var times = service.listBookedTimes(email, day).stream()
                .map(t -> t.format(DateTimeFormatter.ofPattern("HH:mm")))
                .toList();
        return ResponseEntity.ok(
                ApiResponse.ok(new AvailabilityResponse(day.toString(), times), "Trainer availability retrieved successfully",
                        java.time.Instant.now().toString(), http.getRequestURI())
        );
    }

    @Operation(summary = "Cancel booking by id (admin only)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "No content")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "Not found",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
        @DeleteMapping("/bookings/{id}")
        public ResponseEntity<Void> delete(@PathVariable long id) {
                access.requireAdmin();
                service.cancelBooking(id);
                return ResponseEntity.noContent().build(); // 204 sin contenido según convención REST
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