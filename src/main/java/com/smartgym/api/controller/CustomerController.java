package com.smartgym.api.controller;

import com.smartgym.api.common.ApiResponse;
import com.smartgym.api.dto.CustomerDto;
import com.smartgym.application.RegistrationService;
import com.smartgym.api.dto.CustomerSummary;
import com.smartgym.application.GymExtensions;
import com.smartgym.model.Customer;
import com.smartgym.security.AccessGuard;
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
        private final AccessGuard access;
        private final RegistrationService registration;

        public CustomerController(SmartGymService service, GymExtensions ext, AccessGuard access,
                                  RegistrationService registration) {
                this.service = service;
                this.ext = ext;
                this.access = access;
                this.registration = registration;
        }

    @Operation(summary = "Create customer (admin only)", description = "Optional dni links the customer's DNI in the same transaction. Nobody is invited.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201", description = "Created",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409", description = "Customer already exists",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @PostMapping
        public ResponseEntity<ApiResponse<?>> create(@Valid @RequestBody CustomerDto dto,
                                                                                                 jakarta.servlet.http.HttpServletRequest req) {
                access.requireAdmin();
                var created = new Customer(dto.email(), dto.name(), dto.age());
                // Alta y (si viene) vínculo de DNI en una transacción; un DNI ajeno da 409 sin crear nada.
                registration.registerCustomer(created, dto.dni());
        return org.springframework.http.ResponseEntity.status(201).body(
                com.smartgym.api.common.ApiResponse.ok(
                        new CustomerSummary(created.getEmail(), created.getName(), created.getAge()), "Customer created successfully", java.time.Instant.now().toString(), req.getRequestURI()
                )
        );
    }

    @Operation(summary = "List customers (admin only)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "OK",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403", description = "Admin role required",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @GetMapping
    public ResponseEntity<ApiResponse<java.util.List<CustomerSummary>>> list(HttpServletRequest req) {
        var list = service.listCustomers().stream()
                .map(c -> new CustomerSummary(c.getEmail(), c.getName(), c.getAge()))
                .toList();
        return ResponseEntity.ok(
                ApiResponse.ok(list, "Customers retrieved successfully", java.time.Instant.now().toString(), req.getRequestURI())
        );
    }

    @Operation(summary = "Get customer by email",
            description = "Admin: any. Customer: only their own. Trainer: only customers with a booking with them.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "OK",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "Not found",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @GetMapping("/{email}")
    public ResponseEntity<ApiResponse<?>> get(@PathVariable String email, HttpServletRequest req) {
        access.requireCustomerDataByEmail(email);
        var c = service.findCustomer(email)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + email));
        return ResponseEntity.ok(
                ApiResponse.ok(new CustomerSummary(c.getEmail(), c.getName(), c.getAge()), "Customer retrieved successfully", java.time.Instant.now().toString(), req.getRequestURI())
        );
    }

    @Operation(summary = "Get customer by DNI",
            description = "Same access rules as get by email.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "OK",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "Not found",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @GetMapping("/by-dni/{dni}")
    public ResponseEntity<ApiResponse<?>> getByDni(@PathVariable String dni, HttpServletRequest req) {
        access.requireCustomerDataByDni(dni);
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
                ApiResponse.ok(new CustomerSummary(c.getEmail(), c.getName(), c.getAge()), "Customer retrieved successfully", java.time.Instant.now().toString(), req.getRequestURI())
        );
    }
}