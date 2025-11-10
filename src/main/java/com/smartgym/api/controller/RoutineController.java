package com.smartgym.api.controller;

import com.smartgym.api.common.ApiResponse;
import com.smartgym.api.dto.RoutineAssignRequest;
import com.smartgym.application.GymExtensions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;

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

    public RoutineController(GymExtensions ext) { this.ext = ext; }

    @Operation(summary = "Assign random weekly routine (Mon–Sat) by DNI")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201", description = "Assigned",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "DNI not linked",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @PostMapping("/assign")
    public ResponseEntity<ApiResponse<Map<?, ?>>> assign(@RequestBody RoutineAssignRequest req, HttpServletRequest http) {
        var email = ext.emailByDni(req.dni()).orElseThrow(() -> new IllegalArgumentException("DNI not linked"));
        var r = ext.assignRandomRoutine(email);
        var planLower = r.getPlan().entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        e -> e.getKey().toString().toLowerCase(),
                        e -> e.getValue()
                ));
        return ResponseEntity.status(201).body(
                ApiResponse.ok(planLower, "Routine assigned successfully.", java.time.Instant.now().toString(), http.getRequestURI())
        );
    }

    @Operation(summary = "Routine history (last is ACTIVE)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "OK",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "DNI not linked",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @GetMapping("/history/{dni}")
    public ResponseEntity<ApiResponse<Object>> history(@PathVariable String dni, HttpServletRequest http) {
        var email = ext.emailByDni(dni).orElseThrow(() -> new IllegalArgumentException("DNI not linked"));
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
                ApiResponse.ok(normalized, "Routine history retrieved successfully.", java.time.Instant.now().toString(), http.getRequestURI())
        );
    }

    @Operation(summary = "Get active routine block for a given day")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "OK",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "DNI not linked / No active routine",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @GetMapping("/active/{dni}")
    public ResponseEntity<ApiResponse<Map<String, String>>> activeForDay(
            @PathVariable String dni, @RequestParam String day, HttpServletRequest http) {

        var email = ext.emailByDni(dni).orElseThrow(() -> new IllegalArgumentException("DNI not linked"));
        var active = ext.activeRoutine(email).orElseThrow(() -> new IllegalArgumentException("No active routine"));
        DayOfWeek d = DayOfWeek.valueOf(day.toUpperCase()); // MONDAY..SATURDAY
        String block = active.getFor(d);
        var payload = java.util.Map.of("day", d.name().toLowerCase(), "block", block);
        return ResponseEntity.ok(
                ApiResponse.ok(payload, "Active routine block retrieved successfully.",
                        java.time.Instant.now().toString(), http.getRequestURI())
        );
    }
}