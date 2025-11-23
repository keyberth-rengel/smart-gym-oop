package com.smartgym.api.controller;

import com.smartgym.api.common.ApiResponse;
import com.smartgym.api.dto.CustomerDto;
import com.smartgym.application.GymExtensions;
import com.smartgym.model.Customer;
import com.smartgym.service.SmartGymService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

@io.swagger.v3.oas.annotations.tags.Tag(name = "Customers")
@RestController
@RequestMapping("/api/v1/customers")
@Validated
public class CustomerController {

        private final SmartGymService service;
        private final GymExtensions ext;

        public CustomerController(SmartGymService service, GymExtensions ext) {
                this.service = service;
                this.ext = ext;
        }

    @Operation(summary = "Create customer")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201", description = "Created",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409", description = "Customer already exists",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @PostMapping
        public ResponseEntity<ApiResponse<?>> create(@Valid @RequestBody CustomerDto dto,
                                                                                                 jakarta.servlet.http.HttpServletRequest req) {
                var created = new Customer(dto.email(), dto.name(), dto.age());
                service.addCustomer(created); // Servicio maneja duplicados y validaciones
        return org.springframework.http.ResponseEntity.status(201).body(
                com.smartgym.api.common.ApiResponse.ok(
                        created, "Customer created successfully", java.time.Instant.now().toString(), req.getRequestURI()
                )
        );
    }

    @Operation(summary = "Get customer by email")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "OK",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "Not found",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @GetMapping("/{email}")
    public ResponseEntity<ApiResponse<?>> get(@PathVariable String email, HttpServletRequest req) {
        var c = service.findCustomer(email)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + email));
        return ResponseEntity.ok(
                ApiResponse.ok(c, "Customer retrieved successfully", java.time.Instant.now().toString(), req.getRequestURI())
        );
    }

    @Operation(summary = "Get customer by DNI")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "OK",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "Not found",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @GetMapping("/by-dni/{dni}")
    public ResponseEntity<ApiResponse<?>> getByDni(@PathVariable String dni, HttpServletRequest req) {
        var emailOpt = ext.emailByDni(dni);
        if (emailOpt.isEmpty()) {
            return ResponseEntity.status(404).body(
                    ApiResponse.fail("NOT_FOUND", "DNI not linked", null,
                            java.time.Instant.now().toString(), req.getRequestURI())
            );
        }
        var c = service.findCustomer(emailOpt.get())
                .orElseThrow(() -> new IllegalArgumentException("Customer not found for DNI: " + dni));
        return ResponseEntity.ok(
                ApiResponse.ok(c, "Customer retrieved successfully", java.time.Instant.now().toString(), req.getRequestURI())
        );
    }
}