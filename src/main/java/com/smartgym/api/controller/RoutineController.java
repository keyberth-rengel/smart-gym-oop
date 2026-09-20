package com.smartgym.api.controller;

import com.smartgym.api.common.ApiResponse;
import com.smartgym.api.dto.RoutineAssignRequest;
import com.smartgym.application.GymExtensions;
import com.smartgym.security.AccessGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.time.DayOfWeek;
import java.util.Map;

@io.swagger.v3.oas.annotations.tags.Tag(name = "Routines")
@RestController
@RequestMapping("/api/v1/routines")
@Validated
public class RoutineController {

    private final GymExtensions ext;
    private final AccessGuard access;

    public RoutineController(GymExtensions ext, AccessGuard access) {
        this.ext = ext;
        this.access = access;
    }

    @Operation(summary = "Assign random weekly routine (Mon–Sat) by DNI or customer email",
            description = "Send exactly one of `dni` or `customer_email`. Admin: any customer. Trainer: only their own "
                    + "customers (those with a booking with them). Customer: 403.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201", description = "Assigned",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400", description = "Neither or both of dni / customer_email were sent",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403", description = "Not allowed to assign a routine to this customer",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "DNI not linked",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @PostMapping("/assign")
    public ResponseEntity<ApiResponse<Map<?, ?>>> assign(@Valid @RequestBody RoutineAssignRequest req, HttpServletRequest http) {
        boolean byDni = req.dni() != null && !req.dni().isBlank();
        if (byDni) {
            access.requireRoutineAssignByDni(req.dni());
        } else {
            access.requireRoutineAssignByEmail(req.customerEmail());
        }
        var email = byDni
                ? ext.emailByDni(req.dni()).orElseThrow(() -> new IllegalArgumentException("DNI not linked"))
                : req.customerEmail();
        var r = ext.assignRandomRoutine(email);
        var planLower = r.getPlan().entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        e -> e.getKey().toString().toLowerCase(),
                        e -> e.getValue()
                ));
        return ResponseEntity.status(201).body(
                ApiResponse.ok(planLower, "Routine assigned successfully", java.time.Instant.now().toString(), http.getRequestURI())
        );
    }

    @Operation(summary = "Routine history (last is ACTIVE)",
            description = "Admin: any. Customer: only their own. Trainer: only customers with a booking with them.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "OK",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "DNI not linked",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @GetMapping("/history/{dni}")
    public ResponseEntity<ApiResponse<Object>> history(@PathVariable String dni, HttpServletRequest http) {
        access.requireCustomerDataByDni(dni);
        var email = ext.emailByDni(dni).orElseThrow(() -> new IllegalArgumentException("DNI not linked"));
        return historyResponse(email, http);
    }

    @Operation(summary = "Routine history by customer email (last is ACTIVE)",
            description = "Same response and access rules as the DNI variant.")
    @GetMapping("/by-email/{email}/history")
    public ResponseEntity<ApiResponse<Object>> historyByEmail(@PathVariable String email, HttpServletRequest http) {
        access.requireCustomerDataByEmail(email);
        return historyResponse(email, http);
    }

    private ResponseEntity<ApiResponse<Object>> historyResponse(String email, HttpServletRequest http) {
        var list = ext.routineHistory(email);
        var normalized = list.stream().map(r -> {
            var lower = r.getPlan().entrySet().stream().collect(
                    java.util.stream.Collectors.toMap(
                            e -> e.getKey().toString().toLowerCase(),
                            e -> e.getValue()
                    )
            );
            return java.util.Map.of(
                    "plan", lower,
                    "created_at", r.getCreatedAt()
            );
        }).toList();
        return ResponseEntity.ok(
                ApiResponse.ok(normalized, "Routine history retrieved successfully", java.time.Instant.now().toString(), http.getRequestURI())
        );
    }

    @Operation(summary = "Get active routine block for a given day",
            description = "Admin: any. Customer: only their own. Trainer: only customers with a booking with them.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "OK",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "DNI not linked / No active routine",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @GetMapping("/active/{dni}")
    public ResponseEntity<ApiResponse<Map<String, String>>> activeForDay(
            @PathVariable String dni, @RequestParam String day, HttpServletRequest http) {
        access.requireCustomerDataByDni(dni);
        // Validar día primero para mostrar errores de enum claramente
        DayOfWeek d = DayOfWeek.valueOf(day.toUpperCase()); // puede lanzar IllegalArgumentException -> 422
        var email = ext.emailByDni(dni).orElseThrow(() -> new IllegalArgumentException("DNI not linked"));
        return activeResponse(email, d, http);
    }

    @Operation(summary = "Get active routine block for a given day, by customer email",
            description = "Same response and access rules as the DNI variant.")
    @GetMapping("/by-email/{email}/active")
    public ResponseEntity<ApiResponse<Map<String, String>>> activeForDayByEmail(
            @PathVariable String email, @RequestParam String day, HttpServletRequest http) {
        access.requireCustomerDataByEmail(email);
        DayOfWeek d = DayOfWeek.valueOf(day.toUpperCase()); // puede lanzar IllegalArgumentException -> 422
        return activeResponse(email, d, http);
    }

    private ResponseEntity<ApiResponse<Map<String, String>>> activeResponse(String email, DayOfWeek d, HttpServletRequest http) {
        var active = ext.activeRoutine(email).orElseThrow(() -> new IllegalArgumentException("No active routine"));
        String block = active.getFor(d);
        var payload = java.util.Map.of("day", d.name().toLowerCase(), "block", block);
        return ResponseEntity.ok(
                ApiResponse.ok(payload, "Active routine block retrieved successfully",
                        java.time.Instant.now().toString(), http.getRequestURI())
        );
    }
}
