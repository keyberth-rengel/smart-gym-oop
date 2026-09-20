package com.smartgym.api.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/** El {@link RoutineAssignRequest} debe traer exactamente uno de dni / customer_email (los vacíos cuentan como ausentes). */
@Documented
@Constraint(validatedBy = ExactlyOneIdentifierValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExactlyOneIdentifier {
    String message() default "Provide exactly one of dni or customer_email";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
