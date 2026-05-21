package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.exceptions.LootboxException;
import com.mirai.inventoryservice.models.audit.AuditLog;
import com.mirai.inventoryservice.models.audit.User;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import com.mirai.inventoryservice.models.lootbox.CoinEconomyConfig;
import com.mirai.inventoryservice.repositories.AuditLogRepository;
import com.mirai.inventoryservice.repositories.CoinEconomyConfigRepository;
import com.mirai.inventoryservice.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoinEconomyServiceTest {

    @Mock private CoinEconomyConfigRepository coinEconomyConfigRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private CoinEconomyService service;

    private final UUID adminId = UUID.randomUUID();

    private CoinEconomyConfig existingConfig;
    private User admin;

    @BeforeEach
    void setUp() {
        existingConfig = CoinEconomyConfig.builder()
                .id(CoinEconomyConfig.SINGLETON_ID)
                .reviewCoinRate(1)
                .build();
        admin = User.builder().id(adminId).fullName("Admin Person").build();
    }

    @Test
    @DisplayName("setReviewRate writes an audit_logs entry with previous + new in field_changes")
    void setRate_writesAuditLogEntry() {
        when(coinEconomyConfigRepository.findById(CoinEconomyConfig.SINGLETON_ID))
                .thenReturn(Optional.of(existingConfig));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));

        service.setReviewRate(3, adminId);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog entry = captor.getValue();
        assertEquals(StockMovementReason.COIN_RATE_CHANGED, entry.getReason());
        assertEquals(admin, entry.getUser());
        assertEquals("Admin Person", entry.getActorName());
        assertNotNull(entry.getFieldChanges());
        assertEquals(1, entry.getFieldChanges().size());
        Map<String, Object> change = entry.getFieldChanges().get(0);
        assertEquals("review_coin_rate", change.get("field"));
        assertEquals(1, change.get("previous"));
        assertEquals(3, change.get("new"));
    }

    @Test
    @DisplayName("setReviewRate rejects a negative value")
    void setRate_rejectsNegative() {
        assertThrows(LootboxException.class, () -> service.setReviewRate(-1, adminId));
        verify(coinEconomyConfigRepository, never()).save(any());
        verify(auditLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("setReviewRate allows zero (admin can pause coin issuance)")
    void setRate_allowsZero() {
        when(coinEconomyConfigRepository.findById(CoinEconomyConfig.SINGLETON_ID))
                .thenReturn(Optional.of(existingConfig));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));

        CoinEconomyConfig result = service.setReviewRate(0, adminId);

        assertEquals(0, result.getReviewCoinRate());
        verify(coinEconomyConfigRepository).save(existingConfig);
        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    @DisplayName("setReviewRate is a no-op when the new value equals the current value")
    void setRate_noOpWhenUnchanged() {
        existingConfig.setReviewCoinRate(5);
        when(coinEconomyConfigRepository.findById(CoinEconomyConfig.SINGLETON_ID))
                .thenReturn(Optional.of(existingConfig));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));

        service.setReviewRate(5, adminId);

        verify(coinEconomyConfigRepository, never()).save(any());
        verify(auditLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("getReviewRate returns the persisted singleton value")
    void getRate_returnsCurrent() {
        existingConfig.setReviewCoinRate(7);
        when(coinEconomyConfigRepository.findById(CoinEconomyConfig.SINGLETON_ID))
                .thenReturn(Optional.of(existingConfig));

        assertEquals(7, service.getReviewRate());
    }
}
