package com.smartgym.api.advice;

import com.smartgym.api.common.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.Map;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> handleMethodArgInvalid(MethodArgumentNotValidException ex,
                                                                 HttpServletRequest req) {
        var details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of("field", fe.getField(), "error", fe.getDefaultMessage()))
                .toList();
        return ResponseEntity.status(400).body(
                ApiResponse.fail("BAD_REQUEST", "Validation failed", details,
                        Instant.now().toString(), req.getRequestURI())
        );
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<?>> handleBind(BindException ex, HttpServletRequest req) {
        var details = ex.getFieldErrors().stream()
                .map(fe -> Map.of("field", fe.getField(), "error", fe.getDefaultMessage()))
                .toList();
        return ResponseEntity.status(400).body(
                ApiResponse.fail("BAD_REQUEST", "Validation failed", details,
                        Instant.now().toString(), req.getRequestURI())
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<?>> handleConstraint(ConstraintViolationException ex,
                                                           HttpServletRequest req) {
        var details = ex.getConstraintViolations().stream()
                .map(v -> Map.of("field", v.getPropertyPath().toString(), "error", v.getMessage()))
                .toList();
        return ResponseEntity.status(400).body(
                ApiResponse.fail("BAD_REQUEST", "Validation failed", details,
                        Instant.now().toString(), req.getRequestURI())
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<?>> handleUnreadable(HttpMessageNotReadableException ex,
                                                           HttpServletRequest req) {
        return ResponseEntity.status(400).body(
                ApiResponse.fail("BAD_REQUEST", "Malformed JSON payload", null,
                        Instant.now().toString(), req.getRequestURI())
        );
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<?>> handleUnsupportedMedia(HttpMediaTypeNotSupportedException ex,
                                                                 HttpServletRequest req) {
        return ResponseEntity.status(415).body(
                ApiResponse.fail("UNSUPPORTED_MEDIA_TYPE", "Content-Type not supported", null,
                        Instant.now().toString(), req.getRequestURI())
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<?>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex,
                                                                 HttpServletRequest req) {
        return ResponseEntity.status(405).body(
                ApiResponse.fail("METHOD_NOT_ALLOWED", "HTTP method not allowed", null,
                        Instant.now().toString(), req.getRequestURI())
        );
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<?>> handleMissingParam(MissingServletRequestParameterException ex,
                                                             HttpServletRequest req) {
        return ResponseEntity.status(400).body(
                ApiResponse.fail("BAD_REQUEST", "Missing required query parameter: " + ex.getParameterName(), null,
                        Instant.now().toString(), req.getRequestURI())
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<?>> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                             HttpServletRequest req) {
        return ResponseEntity.status(400).body(
                ApiResponse.fail("BAD_REQUEST", "Invalid parameter: " + ex.getName(), null,
                        Instant.now().toString(), req.getRequestURI())
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<?>> handleIllegalArg(IllegalArgumentException ex,
                                                           HttpServletRequest req) {
        return ResponseEntity.status(422).body(
                ApiResponse.fail("UNPROCESSABLE_ENTITY", ex.getMessage(), null,
                        Instant.now().toString(), req.getRequestURI())
        );
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<?>> handleIllegalState(IllegalStateException ex,
                                                             HttpServletRequest req) {
        return ResponseEntity.status(409).body(
                ApiResponse.fail("CONFLICT", ex.getMessage(), null,
                        Instant.now().toString(), req.getRequestURI())
        );
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleNotFound(NoHandlerFoundException ex,
                                                         HttpServletRequest req) {
        return ResponseEntity.status(404).body(
                ApiResponse.fail("NOT_FOUND", "Route not found", null,
                        Instant.now().toString(), req.getRequestURI())
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleResourceNotFound(NoResourceFoundException ex,
                                                                 HttpServletRequest req) {
        return ResponseEntity.status(404).body(
                ApiResponse.fail("NOT_FOUND", "Resource not found", null,
                        Instant.now().toString(), req.getRequestURI())
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleOther(Exception ex, HttpServletRequest req) {
        return ResponseEntity.status(500).body(
                ApiResponse.fail("INTERNAL_ERROR", "Unexpected error", null,
                        Instant.now().toString(), req.getRequestURI())
        );
    }
}