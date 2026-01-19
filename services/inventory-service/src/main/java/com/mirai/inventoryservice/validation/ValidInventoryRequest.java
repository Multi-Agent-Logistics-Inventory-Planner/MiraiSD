package com.mirai.inventoryservice.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = InventoryRequestValidator.class)
@Documented
public @interface ValidInventoryRequest {
    String message() default "Invalid inventory request";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

