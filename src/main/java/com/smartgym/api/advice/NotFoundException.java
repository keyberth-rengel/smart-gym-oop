package com.smartgym.api.advice;

/**
 * Recurso inexistente o identidad no vinculada (cliente, entrenador, reserva, DNI, rutina activa...).
 * Se mapea a HTTP 404 con el formato ApiResponse. Los mensajes se conservan tal cual.
 */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) { super(message); }
}
