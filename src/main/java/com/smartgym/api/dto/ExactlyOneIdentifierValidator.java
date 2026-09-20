package com.smartgym.api.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Reporta el error en ambos campos para que el cliente pueda mostrarlo junto a sus inputs. */
public class ExactlyOneIdentifierValidator implements ConstraintValidator<ExactlyOneIdentifier, RoutineAssignRequest> {

    @Override
    public boolean isValid(RoutineAssignRequest req, ConstraintValidatorContext ctx) {
        if (req == null) return true;
        boolean hasDni = req.dni() != null && !req.dni().isBlank();
        boolean hasEmail = req.customerEmail() != null && !req.customerEmail().isBlank();
        if (hasDni != hasEmail) return true;
        ctx.disableDefaultConstraintViolation();
        String msg = hasDni ? "Send only one of dni or customer_email" : "Provide exactly one of dni or customer_email";
        ctx.buildConstraintViolationWithTemplate(msg).addPropertyNode("dni").addConstraintViolation();
        ctx.buildConstraintViolationWithTemplate(msg).addPropertyNode("customerEmail").addConstraintViolation();
        return false;
    }
}
