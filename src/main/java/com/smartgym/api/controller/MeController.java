package com.smartgym.api.controller;

import com.smartgym.api.common.ApiResponse;
import com.smartgym.api.dto.MeResponse;
import com.smartgym.api.dto.OnboardingRequest;
import com.smartgym.security.CurrentUser;
import com.smartgym.service.MeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@io.swagger.v3.oas.annotations.tags.Tag(name = "Me")
@RestController
@RequestMapping("/api/v1/me")
@Validated
public class MeController {

    private final MeService service;
    private final CurrentUser currentUser;

    public MeController(MeService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @Operation(summary = "Current user: role, profile and whether registration is complete")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "OK",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403", description = "Token without email claim",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @GetMapping
    public ResponseEntity<ApiResponse<MeResponse>> me(HttpServletRequest req) {
        MeResponse me = service.me(currentUser.role(), currentUser.email());
        return ResponseEntity.ok(
                ApiResponse.ok(me, "Current user retrieved successfully", Instant.now().toString(), req.getRequestURI()));
    }

    @Operation(summary = "Complete the customer profile (creates the customer and links the DNI). Customers only.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201", description = "Profile created",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Already completed (idempotent)",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409", description = "DNI linked to another account, or this account has another DNI",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @PostMapping("/onboarding")
    public ResponseEntity<ApiResponse<MeResponse>> onboarding(@Valid @RequestBody OnboardingRequest body,
                                                              HttpServletRequest req) {
        String email = currentUser.email();
        var result = service.onboard(email, body.name(), body.age(), body.dni());
        MeResponse me = service.me(currentUser.role(), email);
        int status = result.changed() ? 201 : 200;
        String message = result.changed() ? "Profile completed successfully" : "Profile already completed";
        return ResponseEntity.status(status).body(
                ApiResponse.ok(me, message, Instant.now().toString(), req.getRequestURI()));
    }
}
