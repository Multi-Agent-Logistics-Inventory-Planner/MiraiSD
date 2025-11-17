package com.pm.inventoryservice.models.converters;

import com.pm.inventoryservice.models.enums.UserRole;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class UserRoleConverter implements AttributeConverter<UserRole, String> {

    @Override
    public String convertToDatabaseColumn(UserRole attribute) {
        return attribute == null ? null : attribute.getDatabaseValue();
        }

    @Override
    public UserRole convertToEntityAttribute(String dbData) {
        return UserRole.fromDatabaseValue(dbData);
    }
}

