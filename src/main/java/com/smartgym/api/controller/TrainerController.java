package com.smartgym.api.controller;

import com.smartgym.api.common.ApiResponse;
import com.smartgym.api.dto.TrainerCreateRequest;
import com.smartgym.api.dto.TrainerCreatedResponse;
import com.smartgym.api.dto.TrainerCustomerItem;
import com.smartgym.api.dto.TrainerDto;
import com.smartgym.application.RegistrationService;
import com.smartgym.clerk.ClerkClient;
import com.smartgym.clerk.InvitationResult;
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

    private static final String TRAINER_ROLE = "entrenador";

    private final SmartGymService service;
    private final AccessGuard access;
    private final RegistrationService registration;
    private final ClerkClient clerk;

    public TrainerController(SmartGymService service, AccessGuard access,
                             RegistrationService registration, ClerkClient clerk) {
        this.service = service;
        this.access = access;
        this.registration = registration;
        this.clerk = clerk;
    }

    @Operation(summary = "Create trainer (admin only) and invite them through Clerk",
            description = "Creates the trainer (and links the optional DNI) in one transaction, then invites the email in Clerk with the "
                    + "'entrenador' role. The trainer is created even if the invitation fails or Clerk is not configured; "
                    + "check 'invitation.status' (INVITED, ROLE_UPDATED, SKIPPED, FAILED).")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201", description = "Created",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409", description = "Trainer already exists or DNI linked to another email",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @PostMapping
    public ResponseEntity<ApiResponse<?>> create(@Valid @RequestBody TrainerCreateRequest dto,
                                                 jakarta.servlet.http.HttpServletRequest req) {
        access.requireAdmin();
        var created = new Trainer(dto.email(), dto.name(), dto.age(), dto.specialty());
        registration.registerTrainer(created, dto.dni());
        // La invitación va DESPUÉS de confirmar la transacción de alta y nunca la deshace.
        InvitationResult invitation = clerk.invite(created.getEmail(), TRAINER_ROLE);
        var body = new TrainerCreatedResponse(created.getEmail(), created.getName(), created.getAge(),
                created.getSpecialty(), dto.dni(), invitation);
        return org.springframework.http.ResponseEntity.status(201).body(
                com.smartgym.api.common.ApiResponse.ok(body, "Trainer created successfully",
                        java.time.Instant.now().toString(), req.getRequestURI())
        );
    }

    @Operation(summary = "Retry the Clerk invitation of an existing trainer (admin only)",
            description = "Always 200 when the trainer exists; read 'status' (INVITED, ROLE_UPDATED, SKIPPED, FAILED) and 'message' (stable code).")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Invitation processed",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403", description = "Admin role required",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @PostMapping("/{email}/invite")
    public ResponseEntity<ApiResponse<?>> invite(@PathVariable String email, HttpServletRequest req) {
        access.requireAdmin();
        var trainer = service.findTrainer(email)
                .orElseThrow(() -> new IllegalArgumentException("Trainer not found: " + email));
        InvitationResult result = clerk.invite(trainer.getEmail(), TRAINER_ROLE);
        return ResponseEntity.ok(
                ApiResponse.ok(result, "Invitation processed", java.time.Instant.now().toString(), req.getRequestURI())
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
