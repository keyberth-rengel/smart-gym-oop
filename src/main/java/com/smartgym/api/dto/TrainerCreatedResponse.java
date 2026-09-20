package com.smartgym.api.dto;

import com.smartgym.clerk.InvitationResult;

/** Entrenador creado más el resultado de invitarlo en Clerk (el alta no falla si la invitación falla o se omite). */
public record TrainerCreatedResponse(String email, String name, int age, String specialty, String dni,
                                     InvitationResult invitation) {}
