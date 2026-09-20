package com.smartgym.api.controller;

import com.smartgym.api.common.ApiResponse;
import com.smartgym.api.dto.IdentityLinkRequest;
import com.smartgym.application.GymExtensions;
import com.smartgym.security.AccessGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

@io.swagger.v3.oas.annotations.tags.Tag(name = "Identity")
@RestController
@RequestMapping("/api/v1/identity")
@Validated
public class IdentityController {

    private final GymExtensions ext;
    private final AccessGuard access;

    public IdentityController(GymExtensions ext, AccessGuard access) {
        this.ext = ext;
        this.access = access;
    }

    @Operation(summary = "Link customer DNI to email (admin only)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201", description = "Linked",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @PostMapping("/customer")
    public ResponseEntity<ApiResponse<?>> linkCustomer(@Valid @RequestBody IdentityLinkRequest req,
                                                       jakarta.servlet.http.HttpServletRequest http) {
        access.requireAdmin();
        ext.registerCustomerIdentity(req.dni(), req.email());
        var payload = java.util.Map.of("dni", req.dni(), "email", req.email());
        return org.springframework.http.ResponseEntity.status(201).body(
                com.smartgym.api.common.ApiResponse.ok(payload, "Customer ID linked successfully",
                        java.time.Instant.now().toString(), http.getRequestURI())
        );
    }

    @Operation(summary = "Link trainer DNI to email (admin only)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201", description = "Linked",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @PostMapping("/trainer")
    public ResponseEntity<ApiResponse<?>> linkTrainer(@Valid @RequestBody IdentityLinkRequest req,
                                                      jakarta.servlet.http.HttpServletRequest http) {
        access.requireAdmin();
        ext.registerTrainerIdentity(req.dni(), req.email());
        var payload = java.util.Map.of("dni", req.dni(), "email", req.email());
        return org.springframework.http.ResponseEntity.status(201).body(
                com.smartgym.api.common.ApiResponse.ok(payload, "Trainer ID linked successfully",
                        java.time.Instant.now().toString(), http.getRequestURI())
        );
    }

    @Operation(summary = "Resolve identity by DNI (admin or the DNI's owner)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "OK",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "Not linked",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @GetMapping("/{dni}")
    public ResponseEntity<ApiResponse<?>> resolve(@PathVariable String dni, jakarta.servlet.http.HttpServletRequest http) {
        access.requireOwnDniOrAdmin(dni);
        var emailOpt = ext.emailByDni(dni);
        if (emailOpt.isEmpty()) {
            return ResponseEntity.status(404).body(
                    ApiResponse.fail("NOT_FOUND", "DNI not linked", null,
                            java.time.Instant.now().toString(), http.getRequestURI())
            );
        }
        var payload = java.util.Map.of("dni", dni, "email", emailOpt.get());
        return ResponseEntity.ok(
                ApiResponse.ok(payload, "Identity resolved successfully",
                        java.time.Instant.now().toString(), http.getRequestURI())
        );
    }
}