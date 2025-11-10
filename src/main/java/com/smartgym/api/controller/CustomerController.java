package com.smartgym.api.controller;

import com.smartgym.api.common.ApiResponse;
import com.smartgym.api.dto.CustomerDto;
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

    public CustomerController(SmartGymService service) {
        this.service = service;
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
        if (service.findCustomer(dto.email()).isPresent()) {
            throw new IllegalStateException("Customer already exists: " + dto.email());
        }
        var created = new Customer(dto.email(), dto.name(), dto.age());
        service.addCustomer(created);
        return org.springframework.http.ResponseEntity.status(201).body(
                com.smartgym.api.common.ApiResponse.ok(
                        created, "Customer created successfully.", java.time.Instant.now().toString(), req.getRequestURI()
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
                ApiResponse.ok(c, "Customer retrieved successfully.", java.time.Instant.now().toString(), req.getRequestURI())
        );
    }
}