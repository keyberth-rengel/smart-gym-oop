package com.smartgym.api.controller;

import com.smartgym.api.common.ApiResponse;
import com.smartgym.api.dto.ProgressCreateRequest;
import com.smartgym.api.dto.ProgressListResponse;
import com.smartgym.application.GymExtensions;
import com.smartgym.security.AccessGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@io.swagger.v3.oas.annotations.tags.Tag(name = "Progress")
@RestController
@RequestMapping("/api/v1/progress")
@Validated
public class ProgressController {

    private final GymExtensions ext;
    private final AccessGuard access;

    public ProgressController(GymExtensions ext, AccessGuard access) {
        this.ext = ext;
        this.access = access;
    }

    @Operation(summary = "Add progress for customer by DNI (today's date)",
            description = "Customer: only their own DNI. Admin: any. Trainer: 403. "
                    + "Optional `date` (yyyy-MM-dd, client-local) must be within one day of the server's UTC date; defaults to the server's date. With `utcOffsetMinutes` the date must be the client's local today.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201", description = "Created",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "DNI not linked",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @PostMapping
    public ResponseEntity<ApiResponse<?>> add(@Valid @RequestBody ProgressCreateRequest req,
                                              jakarta.servlet.http.HttpServletRequest http) {
        access.requireCustomerWriteByDni(req.getDni());
        validateRanges(req);
        java.time.LocalDate date = null;
        if (req.getDate() != null && !req.getDate().isBlank()) {
            try {
                date = java.time.LocalDate.parse(req.getDate());
            } catch (java.time.format.DateTimeParseException ex) {
                throw new IllegalArgumentException("Date must be a valid calendar date (yyyy-MM-dd).");
            }
        }
        ext.addProgressByDni(req.getDni(), req.getWeightKg(), req.getBodyFatPct(), req.getMusclePct(), date, req.getUtcOffsetMinutes());
        var list = ext.progressByDni(req.getDni());
        var last = list.isEmpty() ? null : list.get(list.size() - 1);

        var payload = (last == null) ? null : new com.smartgym.api.dto.ProgressItemResponse(
                last.getDate(), last.getWeightKg(), last.getBodyFatPct(), last.getMusclePct()
        );

        return org.springframework.http.ResponseEntity.status(201).body(
                com.smartgym.api.common.ApiResponse.ok(
                        payload,
                        "Progress record added successfully",
                        java.time.Instant.now().toString(),
                        http.getRequestURI()
                )
        );
    }

    @Operation(summary = "List progress by DNI (with totals and averages)",
            description = "Admin: any. Customer: only their own. Trainer: only customers with a booking with them.")
    @GetMapping("/{dni}")
    public ResponseEntity<ApiResponse<ProgressListResponse>> list(@PathVariable String dni,
                                                                  jakarta.servlet.http.HttpServletRequest http) {
        access.requireCustomerDataByDni(dni);
        return listResponse(ext.progressByDni(dni), http);
    }

    @Operation(summary = "List progress by customer email (with totals and averages)",
            description = "Same response and access rules as the DNI variant.")
    @GetMapping("/by-email/{email}")
    public ResponseEntity<ApiResponse<ProgressListResponse>> listByEmail(@PathVariable String email,
                                                                         jakarta.servlet.http.HttpServletRequest http) {
        access.requireCustomerDataByEmail(email);
        return listResponse(ext.progressByEmail(email), http);
    }

    private ResponseEntity<ApiResponse<ProgressListResponse>> listResponse(
            java.util.List<com.smartgym.domain.ProgressRecord> list, jakarta.servlet.http.HttpServletRequest http) {
        java.util.List<com.smartgym.api.dto.ProgressItemResponse> items = list.stream()
                .map(p -> new com.smartgym.api.dto.ProgressItemResponse(
                        p.getDate(), p.getWeightKg(), p.getBodyFatPct(), p.getMusclePct()
                ))
                .toList();
        double avgW  = list.stream().mapToDouble(com.smartgym.domain.ProgressRecord::getWeightKg).average().orElse(0);
        double avgBF = list.stream().mapToDouble(com.smartgym.domain.ProgressRecord::getBodyFatPct).average().orElse(0);
        double avgM  = list.stream().mapToDouble(com.smartgym.domain.ProgressRecord::getMusclePct).average().orElse(0);
        var payload = new com.smartgym.api.dto.ProgressListResponse(items, items.size(), avgW, avgBF, avgM);
        return org.springframework.http.ResponseEntity.ok(
                com.smartgym.api.common.ApiResponse.ok(
                        payload,
                        "Progress history retrieved successfully",
                        java.time.Instant.now().toString(),
                        http.getRequestURI()
                )
        );
    }

        /**
         * Validación de rangos antes de acceder a la base para devolver 422 claros.
         */
        private void validateRanges(ProgressCreateRequest req) {
                if (req.getWeightKg() == null || req.getWeightKg() <= 0 || req.getWeightKg() > 400) {
                        throw new com.smartgym.api.advice.DomainValidationException("Weight must be between 0 and 400 kg.");
                }
                if (req.getBodyFatPct() == null || req.getBodyFatPct() < 0 || req.getBodyFatPct() > 100) {
                        throw new com.smartgym.api.advice.DomainValidationException("Body fat percentage must be between 0 and 100.");
                }
                if (req.getMusclePct() == null || req.getMusclePct() < 0 || req.getMusclePct() > 100) {
                        throw new com.smartgym.api.advice.DomainValidationException("Muscle percentage must be between 0 and 100.");
                }
        }
}
