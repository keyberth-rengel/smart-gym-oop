package com.smartgym.api.controller;

import com.smartgym.api.common.ApiResponse;
import com.smartgym.api.dto.TrainerDto;
import com.smartgym.model.Trainer;
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

    public TrainerController(SmartGymService service) { this.service = service; }

    @Operation(summary = "Create trainer")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201", description = "Created",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409", description = "Trainer already exists",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @PostMapping
    public ResponseEntity<ApiResponse<?>> create(@Valid @RequestBody TrainerDto dto,
                                                 jakarta.servlet.http.HttpServletRequest req) {
        if (service.findTrainer(dto.email()).isPresent()) {
            throw new IllegalStateException("Trainer already exists: " + dto.email());
        }
        var created = new Trainer(dto.email(), dto.name(), dto.age(), dto.specialty());
        service.addTrainer(created);
        return org.springframework.http.ResponseEntity.status(201).body(
                com.smartgym.api.common.ApiResponse.ok(
                        created, "Trainer created successfully.", java.time.Instant.now().toString(), req.getRequestURI()
                )
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
                ApiResponse.ok(t, "Trainer retrieved successfully.", java.time.Instant.now().toString(), req.getRequestURI())
        );
    }
}