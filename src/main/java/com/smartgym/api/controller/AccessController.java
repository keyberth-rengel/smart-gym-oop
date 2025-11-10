package com.smartgym.api.controller;

import com.smartgym.api.common.ApiResponse;
import com.smartgym.api.dto.AccessRequest;
import com.smartgym.application.GymExtensions;
import com.smartgym.domain.AttendanceRecord;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

@io.swagger.v3.oas.annotations.tags.Tag(name = "Access")
@RestController
@RequestMapping("/api/v1")
@Validated
public class AccessController {

    private final GymExtensions ext;

    public AccessController(GymExtensions ext) { this.ext = ext; }

    @Operation(summary = "Register access by DNI")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "OK",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @PostMapping("/access")
    public ResponseEntity<ApiResponse<String>> access(@RequestBody AccessRequest req, HttpServletRequest http) {
        String msg = ext.accessByDni(req.dni());
        return ResponseEntity.ok(
                ApiResponse.ok(msg, "Access registered successfully.", java.time.Instant.now().toString(), http.getRequestURI())
        );
    }

    @Operation(summary = "List attendance by DNI")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "OK",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "DNI not linked",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @GetMapping("/attendance/{dni}")
    public ResponseEntity<ApiResponse<List<AttendanceRecord>>> list(@PathVariable String dni, HttpServletRequest http) {
        var emailOpt = ext.emailByDni(dni);
        if (emailOpt.isEmpty()) {
            throw new IllegalArgumentException("DNI not linked");
        }
        var list = ext.attendanceByEmail(emailOpt.get());
        return ResponseEntity.ok(
                ApiResponse.ok(list, "Attendance records retrieved successfully.", java.time.Instant.now().toString(), http.getRequestURI())
        );
    }
}