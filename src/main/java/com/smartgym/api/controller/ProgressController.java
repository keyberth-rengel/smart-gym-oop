package com.smartgym.api.controller;

import com.smartgym.api.common.ApiResponse;
import com.smartgym.api.dto.ProgressCreateRequest;
import com.smartgym.api.dto.ProgressListResponse;
import com.smartgym.application.GymExtensions;
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

    public ProgressController(GymExtensions ext) { this.ext = ext; }

    @Operation(summary = "Add progress for customer by DNI (today's date)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201", description = "Created",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "DNI not linked",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @PostMapping
    public ResponseEntity<ApiResponse<?>> add(@Valid @RequestBody ProgressCreateRequest req,
                                              jakarta.servlet.http.HttpServletRequest http) {
        validateRanges(req);
        ext.addProgressByDni(req.getDni(), req.getWeightKg(), req.getBodyFatPct(), req.getMusclePct());
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

    @Operation(summary = "List progress by DNI (with totals and averages)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "OK",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "DNI not linked",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @GetMapping("/{dni}")
    public ResponseEntity<ApiResponse<ProgressListResponse>> list(@PathVariable String dni,
                                                                  jakarta.servlet.http.HttpServletRequest http) {
        var list = ext.progressByDni(dni);

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
