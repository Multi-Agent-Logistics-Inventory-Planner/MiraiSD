package com.mirai.inventoryservice.converters;

import com.mirai.inventoryservice.models.enums.UserRole;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class UserRoleConverter implements AttributeConverter<UserRole, String> {

    @Override
    public String convertToDatabaseColumn(UserRole role) {
        if (role == null) return null;
        return switch (role) {
            case ADMIN -> "admin";
            case EMPLOYEE -> "employee";
        };
    }

    @Override
    public UserRole convertToEntityAttribute(String dbValue) {
        if (dbValue == null) return null;
        return switch (dbValue) {
            case "admin" -> UserRole.ADMIN;
            case "employee" -> UserRole.EMPLOYEE;
            default -> throw new IllegalArgumentException("Unknown UserRole: " + dbValue);
        };
    }
}
