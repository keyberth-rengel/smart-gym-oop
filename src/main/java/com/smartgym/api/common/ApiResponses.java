package com.smartgym.api.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;

import java.time.Instant;

/**
 * Helper para construir respuestas con el envelope ApiResponse<T>.
 * Uso:
 *   return ApiResponses.ok(req, data);
 *   return ApiResponses.created(req, data);
 */
public final class ApiResponses {
    private ApiResponses() {}

    public static <T> ResponseEntity<ApiResponse<T>> ok(HttpServletRequest req, T data) {
        return ResponseEntity.ok(
                ApiResponse.ok(data, null, now(), path(req))
        );
    }

    public static <T> ResponseEntity<ApiResponse<T>> created(HttpServletRequest req, T data) {
        return ResponseEntity.status(201).body(
                ApiResponse.ok(data, null, now(), path(req))
        );
    }

    public static <T> ResponseEntity<ApiResponse<T>> okMsg(HttpServletRequest req, T data, String message) {
        return ResponseEntity.ok(
                ApiResponse.ok(data, message, now(), path(req))
        );
    }

    private static String now() {
        return Instant.now().toString();
    }

    private static String path(HttpServletRequest req) {
        return (req != null) ? req.getRequestURI() : "";
    }
}