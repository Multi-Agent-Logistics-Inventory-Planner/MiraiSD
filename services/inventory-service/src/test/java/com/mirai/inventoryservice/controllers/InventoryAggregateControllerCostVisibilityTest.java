package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.dtos.responses.InventoryTotalDTO;
import com.mirai.inventoryservice.identity.domain.AuthenticatedPrincipal;
import com.mirai.inventoryservice.inventory.infrastructure.InventoryTotalsRepository;
import com.mirai.inventoryservice.inventory.application.InventoryAggregateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for the InventoryTotalDTO.unitCost exposure gap - GET /api/inventory/totals
 * returned unitCost to every role (including EMPLOYEE) with no stripping, unlike the frontend's
 * canViewCosts gate.
 */
@ExtendWith(MockitoExtension.class)
class InventoryAggregateControllerCostVisibilityTest {

    @Mock
    private InventoryAggregateService inventoryAggregateService;

    @Mock
    private InventoryTotalsRepository inventoryTotalsRepository;

    private InventoryTotalDTO totalWithCost() {
        return InventoryTotalDTO.builder()
                .itemId(UUID.randomUUID())
                .sku("SKU-1")
                .name("Item")
                .unitCost(7.5)
                .isActive(true)
                .totalQuantity(3)
                .build();
    }

    private static Authentication authWithRole(String role) {
        Authentication authentication = mock(Authentication.class);
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                UUID.randomUUID(), UUID.randomUUID(), "test@test.internal", "Test User", role, false);
        when(authentication.getPrincipal()).thenReturn(principal);
        return authentication;
    }

    @Test
    void adminSeesUnitCost() {
        when(inventoryTotalsRepository.findAllInventoryTotals())
                .thenReturn(new java.util.ArrayList<>(List.of(totalWithCost())));
        InventoryAggregateController controller =
                new InventoryAggregateController(inventoryAggregateService, inventoryTotalsRepository);
        SecurityContextHolder.getContext().setAuthentication(authWithRole("ADMIN"));
        try {
            ResponseEntity<List<InventoryTotalDTO>> response = controller.getInventoryTotals();
            assertEquals(7.5, response.getBody().get(0).getUnitCost());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void employeeDoesNotSeeUnitCost() {
        when(inventoryTotalsRepository.findAllInventoryTotals())
                .thenReturn(new java.util.ArrayList<>(List.of(totalWithCost())));
        InventoryAggregateController controller =
                new InventoryAggregateController(inventoryAggregateService, inventoryTotalsRepository);
        SecurityContextHolder.getContext().setAuthentication(authWithRole("EMPLOYEE"));
        try {
            ResponseEntity<List<InventoryTotalDTO>> response = controller.getInventoryTotals();
            assertNull(response.getBody().get(0).getUnitCost());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
