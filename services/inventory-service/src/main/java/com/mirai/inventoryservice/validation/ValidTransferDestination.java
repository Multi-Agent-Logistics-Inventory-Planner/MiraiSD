package com.mirai.inventoryservice.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a transfer request has either destinationInventoryId or destinationLocationId.
 * At least one must be provided, but not necessarily both.
 */
@Documented
@Constraint(validatedBy = TransferDestinationValidator.class)
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidTransferDestination {
    String message() default "Either destinationInventoryId or destinationLocationId must be provided";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
