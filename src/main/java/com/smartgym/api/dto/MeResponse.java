package com.smartgym.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Usuario autenticado: rol, perfil y si completó su registro")
public record MeResponse(
        String role,
        String email,
        String name,
        String dni,
        boolean profileComplete,
        ProfileDto profile
) {}
