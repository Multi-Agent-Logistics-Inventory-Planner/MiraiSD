package com.pm.inventoryservice.models.enums;

public enum UserRole {
    MANAGER("manager"),
    EMPLOYEE("employee");

    private final String databaseValue;

    UserRole(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String getDatabaseValue() {
        return databaseValue;
    }

    public static UserRole fromDatabaseValue(String databaseValue) {
        if (databaseValue == null) {
            return null;
        }
        for (UserRole role : values()) {
            if (role.databaseValue.equalsIgnoreCase(databaseValue)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown UserRole database value: " + databaseValue);
    }
}
