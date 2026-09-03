package com.mirai.inventoryservice.identity.domain;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cross-side parity assertions ported from apps/web/src/lib/rbac/role-permissions.test.ts.
 * Both definitions must stay identical - if a permission changes on one side without the
 * other, one of these two suites should fail.
 */
class RolePermissionsTest {

    @Test
    void adminHasAllPermissions() {
        Set<Permission> adminPermissions = RolePermissions.forRole(UserRole.ADMIN);
        assertEquals(Permission.values().length, adminPermissions.size());
        for (Permission permission : Permission.values()) {
            assertTrue(adminPermissions.contains(permission), permission + " missing from ADMIN");
        }
    }

    @Test
    void employeeHasLimitedPermissions() {
        Set<Permission> employeePermissions = RolePermissions.forRole(UserRole.EMPLOYEE);

        assertTrue(employeePermissions.contains(Permission.PRODUCTS_VIEW));
        assertTrue(employeePermissions.contains(Permission.STORAGE_VIEW));
        assertTrue(employeePermissions.contains(Permission.SHIPMENTS_VIEW));
        assertTrue(employeePermissions.contains(Permission.SHIPMENTS_RECEIVE));
        assertTrue(employeePermissions.contains(Permission.SETTINGS_VIEW));
        assertTrue(employeePermissions.contains(Permission.NOTIFICATIONS_VIEW));
        // Team page is view-only for employees; admin actions inside the tabs stay gated
        // by USERS_MANAGE / role checks in the UI.
        assertTrue(employeePermissions.contains(Permission.TEAM_VIEW));

        assertFalse(employeePermissions.contains(Permission.DASHBOARD_VIEW));
        assertFalse(employeePermissions.contains(Permission.PRODUCTS_CREATE));
        assertFalse(employeePermissions.contains(Permission.ANALYTICS_VIEW));
        assertFalse(employeePermissions.contains(Permission.SHIPMENTS_CREATE));
        assertFalse(employeePermissions.contains(Permission.SHIPMENTS_UPDATE));
        assertFalse(employeePermissions.contains(Permission.STORAGE_UPDATE));
        assertFalse(employeePermissions.contains(Permission.STORAGE_DELETE));
    }

    @Test
    void assistantManagerExcludesAdminOnlyPermissions() {
        Set<Permission> assistantManagerPermissions = RolePermissions.forRole(UserRole.ASSISTANT_MANAGER);

        assertFalse(assistantManagerPermissions.contains(Permission.COSTS_VIEW));
        assertFalse(assistantManagerPermissions.contains(Permission.USERS_MANAGE));
        assertTrue(assistantManagerPermissions.contains(Permission.PRODUCTS_CREATE));
        assertTrue(assistantManagerPermissions.contains(Permission.MSRP_VIEW));
        assertTrue(assistantManagerPermissions.contains(Permission.KUJI_PRICES_VIEW));
    }

    @Test
    void onlyAdminAndAssistantManagerSeeMsrpAndKujiPrices() {
        assertFalse(RolePermissions.forRole(UserRole.EMPLOYEE).contains(Permission.MSRP_VIEW));
        assertFalse(RolePermissions.forRole(UserRole.EMPLOYEE).contains(Permission.KUJI_PRICES_VIEW));
    }

    @Test
    void onlyAdminSeesCosts() {
        assertTrue(RolePermissions.forRole(UserRole.ADMIN).contains(Permission.COSTS_VIEW));
        assertFalse(RolePermissions.forRole(UserRole.ASSISTANT_MANAGER).contains(Permission.COSTS_VIEW));
        assertFalse(RolePermissions.forRole(UserRole.EMPLOYEE).contains(Permission.COSTS_VIEW));
    }

    @Test
    void hasPermissionReturnsFalseForNullRole() {
        assertFalse(RolePermissions.hasPermission((UserRole) null, Permission.DASHBOARD_VIEW));
        assertEquals(Set.of(), RolePermissions.forRole(null));
    }

    @Test
    void hasPermissionByRoleNameIsCaseInsensitive() {
        assertTrue(RolePermissions.hasPermission("admin", Permission.COSTS_VIEW));
        assertTrue(RolePermissions.hasPermission("ADMIN", Permission.COSTS_VIEW));
    }

    @Test
    void hasPermissionByRoleNameReturnsFalseForUnrecognizedRole() {
        // JwtAuthenticationFilter's fallback for an authenticated-but-unregistered caller.
        assertFalse(RolePermissions.hasPermission("USER", Permission.PRODUCTS_VIEW));
        assertFalse(RolePermissions.hasPermission((String) null, Permission.PRODUCTS_VIEW));
    }

    @Test
    void hasPermissionByAuthenticationReturnsFalseWhenPrincipalIsNotAuthenticatedPrincipal() {
        org.springframework.security.core.Authentication authentication =
                org.mockito.Mockito.mock(org.springframework.security.core.Authentication.class);
        org.mockito.Mockito.when(authentication.getPrincipal()).thenReturn("not-a-principal");
        assertFalse(RolePermissions.hasPermission(authentication, Permission.PRODUCTS_VIEW));
        assertFalse(RolePermissions.hasPermission((org.springframework.security.core.Authentication) null, Permission.PRODUCTS_VIEW));
    }
}
