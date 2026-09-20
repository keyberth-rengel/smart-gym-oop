package com.smartgym.api.controller;

import com.smartgym.api.common.ApiResponse;
import com.smartgym.api.dto.TrainerCustomerItem;
import com.smartgym.api.dto.TrainerDto;
import java.util.List;
import com.smartgym.model.Trainer;
import com.smartgym.security.AccessGuard;
import com.smartgym.service.SmartGymService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

@io.swagger.v3.oas.annotations.tags.Tag(name = "Trainers")
@RestController
@RequestMapping("/api/v1/trainers")
@Validated
public class TrainerController {

    private final SmartGymService service;
    private final AccessGuard access;

    public TrainerController(SmartGymService service, AccessGuard access) {
        this.service = service;
        this.access = access;
    }

    @Operation(summary = "Create trainer (admin only)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201", description = "Created",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409", description = "Trainer already exists",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @PostMapping
    public ResponseEntity<ApiResponse<?>> create(@Valid @RequestBody TrainerDto dto,
                                                 jakarta.servlet.http.HttpServletRequest req) {
        access.requireAdmin();
        if (service.findTrainer(dto.email()).isPresent()) {
            throw new IllegalStateException("Trainer already exists: " + dto.email());
        }
        var created = new Trainer(dto.email(), dto.name(), dto.age(), dto.specialty());
        service.addTrainer(created);
        return org.springframework.http.ResponseEntity.status(201).body(
                com.smartgym.api.common.ApiResponse.ok(
                        new TrainerDto(created.getEmail(), created.getName(), created.getAge(), created.getSpecialty()),
                        "Trainer created successfully", java.time.Instant.now().toString(), req.getRequestURI()
                )
        );
    }

    @Operation(summary = "List trainers (any authenticated user; clients need it to book)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "OK",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @GetMapping
    public ResponseEntity<ApiResponse<List<TrainerDto>>> list(HttpServletRequest req) {
        var list = service.listTrainers().stream()
                .map(t -> new TrainerDto(t.getEmail(), t.getName(), t.getAge(), t.getSpecialty()))
                .toList();
        return ResponseEntity.ok(
                ApiResponse.ok(list, "Trainers retrieved successfully", java.time.Instant.now().toString(), req.getRequestURI())
        );
    }

    @Operation(summary = "Get trainer by email")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "OK",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "Not found",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @GetMapping("/{email}")
    public ResponseEntity<ApiResponse<?>> get(@PathVariable String email, HttpServletRequest req) {
        var t = service.findTrainer(email)
                .orElseThrow(() -> new IllegalArgumentException("Trainer not found: " + email));
        return ResponseEntity.ok(
                ApiResponse.ok(new TrainerDto(t.getEmail(), t.getName(), t.getAge(), t.getSpecialty()),
                        "Trainer retrieved successfully", java.time.Instant.now().toString(), req.getRequestURI())
        );
    }

    @Operation(summary = "List a trainer's customers",
            description = "Distinct customers with at least one booking with the trainer, with their number of sessions and "
                    + "most recent booking, newest first. Admin: any trainer. Trainer: only their own. Customers: 403.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "OK",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403", description = "Not allowed to see this trainer's customers",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "422", description = "Trainer not found",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @GetMapping("/{email}/customers")
    public ResponseEntity<ApiResponse<List<TrainerCustomerItem>>> customers(@PathVariable String email, HttpServletRequest req) {
        access.requireTrainerScope(email);
        var list = service.listTrainerCustomers(email).stream()
                .map(r -> new TrainerCustomerItem(r.email(), r.name(), r.age(), r.sessions(),
                        r.lastDate().toString(), r.lastTime().toString()))
                .toList();
        return ResponseEntity.ok(
                ApiResponse.ok(list, "Trainer customers retrieved successfully", java.time.Instant.now().toString(), req.getRequestURI())
        );
    }
}
