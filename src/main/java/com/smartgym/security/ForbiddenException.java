package com.smartgym.security;

/** Acceso denegado por regla de negocio o por datos faltantes en el token. Se mapea a HTTP 403. */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) { super(message); }
}
