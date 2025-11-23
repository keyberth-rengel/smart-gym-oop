package com.smartgym.api.advice;

/**
 * Excepción de validación de dominio: regla de negocio violada tras pasar validación estructural.
 * Se mapea a HTTP 422.
 */
public class DomainValidationException extends RuntimeException {
    public DomainValidationException(String message) { super(message); }
}