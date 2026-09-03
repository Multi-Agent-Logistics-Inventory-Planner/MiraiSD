package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.dtos.responses.kuji.KujiBoxResponseDTO;
import com.mirai.inventoryservice.dtos.responses.kuji.KujiBoxTierResponseDTO;
import com.mirai.inventoryservice.dtos.responses.kuji.KujiDailyPayoutsResponseDTO;
import com.mirai.inventoryservice.identity.domain.AuthenticatedPrincipal;
import com.mirai.inventoryservice.services.KujiBoxService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for the kuji tier price exposure gap - KujiBoxController's ~20 return
 * points carried no field-level stripping, so price/linkedProductPrice reached every role even
 * though the frontend hides them behind canViewKujiPrices.
 */
@ExtendWith(MockitoExtension.class)
class KujiBoxControllerPriceVisibilityTest {

    @Mock
    private KujiBoxService kujiBoxService;

    private static Authentication authWithRole(String role) {
        Authentication authentication = mock(Authentication.class);
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                UUID.randomUUID(), UUID.randomUUID(), "test@test.internal", "Test User", role);
        when(authentication.getPrincipal()).thenReturn(principal);
        return authentication;
    }

    private static KujiBoxTierResponseDTO tierWithPrices() {
        return KujiBoxTierResponseDTO.builder()
                .id(UUID.randomUUID())
                .price(BigDecimal.valueOf(5))
                .linkedProductPrice(BigDecimal.valueOf(10))
                .build();
    }

    private static KujiBoxResponseDTO boxWithTier() {
        return KujiBoxResponseDTO.builder()
                .id(UUID.randomUUID())
                .tiers(List.of(tierWithPrices()))
                .build();
    }

    @Test
    void adminSeesTierPricesOnGetBox() {
        when(kujiBoxService.getBox(org.mockito.ArgumentMatchers.any())).thenReturn(boxWithTier());
        KujiBoxController controller = new KujiBoxController(kujiBoxService);

        ResponseEntity<KujiBoxResponseDTO> response = controller.getBox(UUID.randomUUID(), authWithRole("ADMIN"));

        KujiBoxTierResponseDTO tier = response.getBody().getTiers().get(0);
        assertEquals(BigDecimal.valueOf(5), tier.getPrice());
        assertEquals(BigDecimal.valueOf(10), tier.getLinkedProductPrice());
    }

    @Test
    void employeeDoesNotSeeTierPricesOnGetBox() {
        when(kujiBoxService.getBox(org.mockito.ArgumentMatchers.any())).thenReturn(boxWithTier());
        KujiBoxController controller = new KujiBoxController(kujiBoxService);

        ResponseEntity<KujiBoxResponseDTO> response = controller.getBox(UUID.randomUUID(), authWithRole("EMPLOYEE"));

        KujiBoxTierResponseDTO tier = response.getBody().getTiers().get(0);
        assertNull(tier.getPrice());
        assertNull(tier.getLinkedProductPrice());
    }

    @Test
    void employeeDoesNotSeeTierPricesOnBoxHistoryOrLastTiers() {
        when(kujiBoxService.getBoxHistory(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new java.util.ArrayList<>(List.of(boxWithTier())));
        when(kujiBoxService.cloneTiersFromLastClosedBox(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new java.util.ArrayList<>(List.of(tierWithPrices())));
        KujiBoxController controller = new KujiBoxController(kujiBoxService);
        Authentication employee = authWithRole("EMPLOYEE");

        ResponseEntity<List<KujiBoxResponseDTO>> historyResponse = controller.getBoxHistory(UUID.randomUUID(), employee);
        assertNull(historyResponse.getBody().get(0).getTiers().get(0).getPrice());

        ResponseEntity<List<KujiBoxTierResponseDTO>> tiersResponse = controller.getLastClosedTiers(UUID.randomUUID(), employee);
        assertNull(tiersResponse.getBody().get(0).getPrice());
        assertNull(tiersResponse.getBody().get(0).getLinkedProductPrice());
    }

    @Test
    void employeeDoesNotSeeTierPricesAfterRecordingADraw() {
        when(kujiBoxService.recordDraw(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(boxWithTier());
        KujiBoxController controller = new KujiBoxController(kujiBoxService);

        ResponseEntity<KujiBoxResponseDTO> response = controller.recordDraw(
                UUID.randomUUID(), null, authWithRole("EMPLOYEE"));

        assertNull(response.getBody().getTiers().get(0).getPrice());
    }

    /**
     * Regression for a follow-up review finding: /daily-payouts returned valueWon
     * unconditionally, the same API/UI mismatch as the tier-price gap above, on an endpoint
     * with no @PreAuthorize at all (open to any authenticated caller, EMPLOYEE included).
     */
    @Test
    void employeeDoesNotSeePayoutValuesOnDailyPayouts() {
        KujiDailyPayoutsResponseDTO payouts = new KujiDailyPayoutsResponseDTO(
                UUID.randomUUID(),
                java.time.LocalDate.of(2026, 1, 1),
                java.time.LocalDate.of(2026, 1, 2),
                "UTC",
                List.of(new KujiDailyPayoutsResponseDTO.DailyPoint(
                        java.time.LocalDate.of(2026, 1, 1), BigDecimal.valueOf(50), 3)),
                new KujiDailyPayoutsResponseDTO.Totals(BigDecimal.valueOf(50), 3));
        when(kujiBoxService.getDailyPayouts(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(payouts);
        KujiBoxController controller = new KujiBoxController(kujiBoxService);

        ResponseEntity<KujiDailyPayoutsResponseDTO> response = controller.getDailyPayouts(
                UUID.randomUUID(), null, null, "UTC", authWithRole("EMPLOYEE"));

        KujiDailyPayoutsResponseDTO body = response.getBody();
        assertNull(body.series().get(0).valueWon());
        assertEquals(3, body.series().get(0).slipCount());
        assertNull(body.total().valueWon());
        assertEquals(3, body.total().slipCount());
    }

    @Test
    void adminSeesPayoutValuesOnDailyPayouts() {
        KujiDailyPayoutsResponseDTO payouts = new KujiDailyPayoutsResponseDTO(
                UUID.randomUUID(),
                java.time.LocalDate.of(2026, 1, 1),
                java.time.LocalDate.of(2026, 1, 2),
                "UTC",
                List.of(new KujiDailyPayoutsResponseDTO.DailyPoint(
                        java.time.LocalDate.of(2026, 1, 1), BigDecimal.valueOf(50), 3)),
                new KujiDailyPayoutsResponseDTO.Totals(BigDecimal.valueOf(50), 3));
        when(kujiBoxService.getDailyPayouts(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(payouts);
        KujiBoxController controller = new KujiBoxController(kujiBoxService);

        ResponseEntity<KujiDailyPayoutsResponseDTO> response = controller.getDailyPayouts(
                UUID.randomUUID(), null, null, "UTC", authWithRole("ADMIN"));

        assertEquals(BigDecimal.valueOf(50), response.getBody().series().get(0).valueWon());
        assertEquals(BigDecimal.valueOf(50), response.getBody().total().valueWon());
    }
}
