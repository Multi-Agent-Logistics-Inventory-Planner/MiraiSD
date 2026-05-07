package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.dtos.requests.AuditLogFilterDTO;
import com.mirai.inventoryservice.integration.BaseKafkaIntegrationTest;
import com.mirai.inventoryservice.models.Category;
import com.mirai.inventoryservice.models.Product;
import com.mirai.inventoryservice.models.audit.AuditLog;
import com.mirai.inventoryservice.models.audit.StockMovement;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the main audit-log list filters out kuji-internal draws and
 * undos, but keeps inventory transfers involving a kuji box. Also verifies
 * that explicit kuji-reason filters bypass the exclusion (used by the kuji
 * activity log card and the undo-draw history list).
 */
class AuditLogSpecificationsIT extends BaseKafkaIntegrationTest {

    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private StockMovementRepository stockMovementRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;

    private Product product;
    private UUID boxId;

    private AuditLog drawLog;
    private AuditLog reversedDrawLog;
    private AuditLog plainAdjustmentLog;
    private AuditLog kujiTransferLog;
    private AuditLog plainTransferLog;
    private AuditLog kujiSlipAdjustmentLog;

    @BeforeEach
    void seedAuditLogs() {
        Category category = categoryRepository.save(
                Category.builder().name("test-cat-" + UUID.randomUUID()).build());

        product = productRepository.save(Product.builder()
                .name("Test Product")
                .sku("SPEC-" + UUID.randomUUID().toString().substring(0, 6))
                .category(category)
                .quantity(0)
                .reorderPoint(0)
                .build());

        boxId = UUID.randomUUID();

        drawLog = saveLog(StockMovementReason.KUJI_PRIZE_WON);
        saveMovement(drawLog, kujiMeta(boxId, "draw"));

        reversedDrawLog = saveLog(StockMovementReason.KUJI_DRAW_REVERSED);
        saveMovement(reversedDrawLog, kujiMeta(boxId, "undo"));

        plainAdjustmentLog = saveLog(StockMovementReason.ADJUSTMENT);
        saveMovement(plainAdjustmentLog, plainMeta());

        kujiTransferLog = saveLog(StockMovementReason.TRANSFER);
        saveMovement(kujiTransferLog, kujiMeta(boxId, "transfer_in_more"));

        plainTransferLog = saveLog(StockMovementReason.TRANSFER);
        saveMovement(plainTransferLog, plainMeta());

        kujiSlipAdjustmentLog = saveLog(StockMovementReason.KUJI_SLIP_ADJUSTMENT);
        saveMovement(kujiSlipAdjustmentLog, kujiMeta(boxId, "kuji_stash_add"));
    }

    @Test
    @DisplayName("Default list (no reason) excludes kuji draws/undos/slip-adjustments but keeps transfers")
    void defaultListExcludesAllKujiReasons() {
        Page<AuditLog> result = run(AuditLogFilterDTO.builder().build());

        assertThat(ids(result))
                .doesNotContain(drawLog.getId(), reversedDrawLog.getId(), kujiSlipAdjustmentLog.getId())
                .contains(plainAdjustmentLog.getId(),
                        kujiTransferLog.getId(), plainTransferLog.getId());
    }

    @Test
    @DisplayName("reason=KUJI_SLIP_ADJUSTMENT returns slip adjustments")
    void kujiSlipAdjustmentReasonReturnsRows() {
        Page<AuditLog> result = run(AuditLogFilterDTO.builder()
                .reason(StockMovementReason.KUJI_SLIP_ADJUSTMENT).build());

        assertThat(ids(result))
                .contains(kujiSlipAdjustmentLog.getId())
                .doesNotContain(drawLog.getId(), reversedDrawLog.getId());
    }

    @Test
    @DisplayName("reasons=[KUJI_PRIZE_WON, KUJI_DRAW_REVERSED, KUJI_SLIP_ADJUSTMENT] returns all three")
    void kujiReasonsListReturnsAllKujiActivity() {
        Page<AuditLog> result = run(AuditLogFilterDTO.builder()
                .reasons(List.of(
                        StockMovementReason.KUJI_PRIZE_WON,
                        StockMovementReason.KUJI_DRAW_REVERSED,
                        StockMovementReason.KUJI_SLIP_ADJUSTMENT))
                .build());

        assertThat(ids(result))
                .contains(drawLog.getId(), reversedDrawLog.getId(), kujiSlipAdjustmentLog.getId())
                .doesNotContain(plainTransferLog.getId(), plainAdjustmentLog.getId());
    }

    @Test
    @DisplayName("reason=TRANSFER keeps kuji transfers visible")
    void transferFilterKeepsKujiTransfers() {
        Page<AuditLog> result = run(AuditLogFilterDTO.builder()
                .reason(StockMovementReason.TRANSFER).build());

        assertThat(ids(result))
                .contains(kujiTransferLog.getId(), plainTransferLog.getId());
    }

    @Test
    @DisplayName("reason=KUJI_PRIZE_WON returns draws (kuji activity log unchanged)")
    void kujiReasonReturnsDraws() {
        Page<AuditLog> result = run(AuditLogFilterDTO.builder()
                .reason(StockMovementReason.KUJI_PRIZE_WON).build());

        assertThat(ids(result))
                .contains(drawLog.getId())
                .doesNotContain(reversedDrawLog.getId(), plainTransferLog.getId());
    }

    @Test
    @DisplayName("reason=KUJI_DRAW_REVERSED returns undos")
    void kujiReversedReasonReturnsUndos() {
        Page<AuditLog> result = run(AuditLogFilterDTO.builder()
                .reason(StockMovementReason.KUJI_DRAW_REVERSED).build());

        assertThat(ids(result))
                .contains(reversedDrawLog.getId())
                .doesNotContain(drawLog.getId());
    }

    // ===================== helpers =====================

    private Page<AuditLog> run(AuditLogFilterDTO filters) {
        return auditLogRepository.findAll(
                AuditLogSpecifications.withFilters(filters),
                PageRequest.of(0, 50));
    }

    private List<UUID> ids(Page<AuditLog> page) {
        return page.getContent().stream().map(AuditLog::getId).toList();
    }

    private AuditLog saveLog(StockMovementReason reason) {
        return auditLogRepository.save(AuditLog.builder()
                .reason(reason)
                .itemCount(1)
                .totalQuantityMoved(1)
                .productSummary("test")
                .build());
    }

    private void saveMovement(AuditLog log, Map<String, Object> metadata) {
        stockMovementRepository.save(StockMovement.builder()
                .auditLog(log)
                .item(product)
                .locationType(LocationType.NOT_ASSIGNED)
                .quantityChange(1)
                .reason(log.getReason())
                .at(OffsetDateTime.now())
                .metadata(metadata)
                .build());
    }

    private Map<String, Object> kujiMeta(UUID boxId, String action) {
        Map<String, Object> m = new HashMap<>();
        m.put("kuji_box_id", boxId.toString());
        m.put("action", action);
        return m;
    }

    private Map<String, Object> plainMeta() {
        Map<String, Object> m = new HashMap<>();
        m.put("source", "manual");
        return m;
    }
}
