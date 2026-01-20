package com.mirai.inventoryservice.models.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum UserRole {
    ADMIN,
    EMPLOYEE;

    @JsonCreator
    public static UserRole fromString(String value) {
        if (value == null) {
            return null;
        }
        return UserRole.valueOf(value.toUpperCase());
    }
}

