package com.smartgym.clerk;

/**
 * Resultado de invitar (o asignar rol) a un usuario en Clerk. {@code message} es un código estable, no un texto de usuario.
 */
public record InvitationResult(Status status, String message) {

    public enum Status { INVITED, ROLE_UPDATED, SKIPPED, FAILED }

    public static InvitationResult invited() { return new InvitationResult(Status.INVITED, "invitation_sent"); }

    public static InvitationResult roleUpdated() { return new InvitationResult(Status.ROLE_UPDATED, "role_updated_existing_account"); }

    public static InvitationResult skipped() { return new InvitationResult(Status.SKIPPED, "clerk_not_configured"); }

    public static InvitationResult failed(String code) { return new InvitationResult(Status.FAILED, code); }
}
