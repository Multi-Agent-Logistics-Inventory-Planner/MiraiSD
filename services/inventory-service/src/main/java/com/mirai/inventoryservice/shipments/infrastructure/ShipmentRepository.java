package com.mirai.inventoryservice.shipments.infrastructure;

import com.mirai.inventoryservice.models.enums.ShipmentStatus;
import com.mirai.inventoryservice.shipments.domain.Shipment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShipmentRepository extends JpaRepository<Shipment, UUID> {
    Optional<Shipment> findFirstByTrackingIdOrderByCreatedAtDesc(String trackingId);

    /**
     * Site-scoped variant (.specs/phase-7-shipments AC-2): the v1/legacy tracking lookup routes
     * use this so a foreign-site tracking number never enriches the response with another site's
     * shipment data.
     */
    Optional<Shipment> findFirstByTrackingIdAndSite_IdOrderByCreatedAtDesc(String trackingId, UUID siteId);

    /**
     * Used by the webhook's tracking-code fallback (.specs/phase-7-shipments AC-2, review
     * finding) to require an unambiguous match: {@code tracking_id} carries no uniqueness
     * constraint, so two shipments -- possibly at two different sites -- can share one, and
     * {@code findFirstByTrackingIdOrderByCreatedAtDesc}'s "most recent" tiebreak could silently
     * attach a real carrier-status update to the wrong site's shipment.
     */
    List<Shipment> findByTrackingId(String trackingId);

    Optional<Shipment> findByEasypostTrackerId(String easypostTrackerId);

    List<Shipment> findByStatusOrderByCreatedAtDesc(ShipmentStatus status);

    List<Shipment> findBySupplierNameContainingIgnoreCaseOrderByCreatedAtDesc(String supplierName);

    List<Shipment> findByOrderDateBetweenOrderByOrderDateDesc(LocalDate startDate, LocalDate endDate);

    List<Shipment> findByExpectedDeliveryDateBetweenOrderByExpectedDeliveryDateAsc(LocalDate startDate, LocalDate endDate);

    /**
     * Site-scoped (.specs/phase-7-shipments AC-2, review finding): every shipment list/count
     * route resolves through the caller's authorized site so a shipment belonging to another
     * site is never returned, even to an authenticated user with no membership at all elsewhere
     * (the legacy routes carried no {@code @PreAuthorize} and, before this, no site check).
     */
    @Query("SELECT DISTINCT s FROM Shipment s " +
            "LEFT JOIN FETCH s.createdBy " +
            "LEFT JOIN FETCH s.receivedBy " +
            "LEFT JOIN FETCH s.items si " +
            "LEFT JOIN FETCH si.item " +
            "WHERE si.item.id = :productId AND s.site.id = :siteId ORDER BY s.orderDate DESC")
    List<Shipment> findByItemsContainingProduct(@Param("productId") UUID productId, @Param("siteId") UUID siteId);

    // Single shipment with JOIN FETCH to avoid N+1
    @Query("SELECT s FROM Shipment s " +
            "LEFT JOIN FETCH s.createdBy " +
            "LEFT JOIN FETCH s.receivedBy " +
            "LEFT JOIN FETCH s.items si " +
            "LEFT JOIN FETCH si.item " +
            "WHERE s.id = :id")
    Optional<Shipment> findByIdWithAssociations(@Param("id") UUID id);

    /**
     * Site-scoped variant of {@link #findByIdWithAssociations} (.specs/phase-7-shipments AC-2):
     * every single-resource read/write path resolves through this so a foreign-site id is
     * indistinguishable from a not-found id, rather than through the unscoped method above,
     * which stays only for legacy compatibility until it is removed.
     */
    @Query("SELECT s FROM Shipment s " +
            "LEFT JOIN FETCH s.createdBy " +
            "LEFT JOIN FETCH s.receivedBy " +
            "LEFT JOIN FETCH s.items si " +
            "LEFT JOIN FETCH si.item " +
            "WHERE s.id = :id AND s.site.id = :siteId")
    Optional<Shipment> findByIdAndSite_IdWithAssociations(@Param("id") UUID id, @Param("siteId") UUID siteId);

    // Non-paginated list with JOIN FETCH to avoid N+1, site-scoped
    @Query("SELECT DISTINCT s FROM Shipment s " +
            "LEFT JOIN FETCH s.createdBy " +
            "LEFT JOIN FETCH s.receivedBy " +
            "LEFT JOIN FETCH s.items si " +
            "LEFT JOIN FETCH si.item " +
            "WHERE s.site.id = :siteId " +
            "ORDER BY s.createdAt DESC")
    List<Shipment> findAllWithAssociationsList(@Param("siteId") UUID siteId);

    @Query("SELECT DISTINCT s FROM Shipment s " +
            "LEFT JOIN FETCH s.createdBy " +
            "LEFT JOIN FETCH s.receivedBy " +
            "LEFT JOIN FETCH s.items si " +
            "LEFT JOIN FETCH si.item " +
            "WHERE s.status = :status AND s.site.id = :siteId " +
            "ORDER BY s.createdAt DESC")
    List<Shipment> findByStatusWithAssociationsList(@Param("status") ShipmentStatus status, @Param("siteId") UUID siteId);

    @Query(value = "SELECT s FROM Shipment s " +
            "LEFT JOIN FETCH s.createdBy " +
            "LEFT JOIN FETCH s.receivedBy " +
            "WHERE (:status IS NULL OR s.status = :status) AND s.site.id = :siteId",
            countQuery = "SELECT COUNT(s) FROM Shipment s " +
            "WHERE (:status IS NULL OR s.status = :status) AND s.site.id = :siteId")
    Page<Shipment> findByStatusPaged(@Param("status") ShipmentStatus status, @Param("siteId") UUID siteId, Pageable pageable);

    @Query(value = "SELECT s FROM Shipment s " +
            "LEFT JOIN FETCH s.createdBy " +
            "LEFT JOIN FETCH s.receivedBy " +
            "WHERE (:status IS NULL OR s.status = :status) AND s.site.id = :siteId " +
            "AND (LOWER(s.shipmentNumber) LIKE :searchPattern OR LOWER(s.supplierName) LIKE :searchPattern)",
            countQuery = "SELECT COUNT(s) FROM Shipment s " +
            "WHERE (:status IS NULL OR s.status = :status) AND s.site.id = :siteId " +
            "AND (LOWER(s.shipmentNumber) LIKE :searchPattern OR LOWER(s.supplierName) LIKE :searchPattern)")
    Page<Shipment> findByStatusAndSearch(@Param("status") ShipmentStatus status, @Param("searchPattern") String searchPattern, @Param("siteId") UUID siteId, Pageable pageable);

    // ACTIVE: PENDING with no receipts and carrier_status NOT in (DELIVERED, FAILED)
    @Query(value = "SELECT s FROM Shipment s " +
            "LEFT JOIN FETCH s.createdBy " +
            "LEFT JOIN FETCH s.receivedBy " +
            "WHERE s.status = com.mirai.inventoryservice.models.enums.ShipmentStatus.PENDING " +
            "AND s.site.id = :siteId " +
            "AND NOT EXISTS (SELECT 1 FROM ShipmentItem si WHERE si.shipment = s AND (si.receivedQuantity + si.damagedQuantity + si.displayQuantity + si.shopQuantity) > 0) " +
            "AND (s.carrierStatus IS NULL " +
            "     OR s.carrierStatus NOT IN (com.mirai.inventoryservice.models.enums.CarrierStatus.DELIVERED, com.mirai.inventoryservice.models.enums.CarrierStatus.FAILED))",
            countQuery = "SELECT COUNT(s) FROM Shipment s " +
            "WHERE s.status = com.mirai.inventoryservice.models.enums.ShipmentStatus.PENDING " +
            "AND s.site.id = :siteId " +
            "AND NOT EXISTS (SELECT 1 FROM ShipmentItem si WHERE si.shipment = s AND (si.receivedQuantity + si.damagedQuantity + si.displayQuantity + si.shopQuantity) > 0) " +
            "AND (s.carrierStatus IS NULL " +
            "     OR s.carrierStatus NOT IN (com.mirai.inventoryservice.models.enums.CarrierStatus.DELIVERED, com.mirai.inventoryservice.models.enums.CarrierStatus.FAILED))")
    Page<Shipment> findActiveShipments(@Param("siteId") UUID siteId, Pageable pageable);

    @Query(value = "SELECT s FROM Shipment s " +
            "LEFT JOIN FETCH s.createdBy " +
            "LEFT JOIN FETCH s.receivedBy " +
            "WHERE s.status = com.mirai.inventoryservice.models.enums.ShipmentStatus.PENDING " +
            "AND s.site.id = :siteId " +
            "AND NOT EXISTS (SELECT 1 FROM ShipmentItem si WHERE si.shipment = s AND (si.receivedQuantity + si.damagedQuantity + si.displayQuantity + si.shopQuantity) > 0) " +
            "AND (s.carrierStatus IS NULL " +
            "     OR s.carrierStatus NOT IN (com.mirai.inventoryservice.models.enums.CarrierStatus.DELIVERED, com.mirai.inventoryservice.models.enums.CarrierStatus.FAILED)) " +
            "AND (LOWER(s.shipmentNumber) LIKE :searchPattern OR LOWER(s.supplierName) LIKE :searchPattern)",
            countQuery = "SELECT COUNT(s) FROM Shipment s " +
            "WHERE s.status = com.mirai.inventoryservice.models.enums.ShipmentStatus.PENDING " +
            "AND s.site.id = :siteId " +
            "AND NOT EXISTS (SELECT 1 FROM ShipmentItem si WHERE si.shipment = s AND (si.receivedQuantity + si.damagedQuantity + si.displayQuantity + si.shopQuantity) > 0) " +
            "AND (s.carrierStatus IS NULL " +
            "     OR s.carrierStatus NOT IN (com.mirai.inventoryservice.models.enums.CarrierStatus.DELIVERED, com.mirai.inventoryservice.models.enums.CarrierStatus.FAILED)) " +
            "AND (LOWER(s.shipmentNumber) LIKE :searchPattern OR LOWER(s.supplierName) LIKE :searchPattern)")
    Page<Shipment> findActiveShipmentsWithSearch(@Param("searchPattern") String searchPattern, @Param("siteId") UUID siteId, Pageable pageable);

    // AWAITING_RECEIPT: PENDING with no receipts and carrier_status = DELIVERED
    @Query(value = "SELECT s FROM Shipment s " +
            "LEFT JOIN FETCH s.createdBy " +
            "LEFT JOIN FETCH s.receivedBy " +
            "WHERE s.status = com.mirai.inventoryservice.models.enums.ShipmentStatus.PENDING " +
            "AND s.site.id = :siteId " +
            "AND s.carrierStatus = com.mirai.inventoryservice.models.enums.CarrierStatus.DELIVERED " +
            "AND NOT EXISTS (SELECT 1 FROM ShipmentItem si WHERE si.shipment = s AND (si.receivedQuantity + si.damagedQuantity + si.displayQuantity + si.shopQuantity) > 0)",
            countQuery = "SELECT COUNT(s) FROM Shipment s " +
            "WHERE s.status = com.mirai.inventoryservice.models.enums.ShipmentStatus.PENDING " +
            "AND s.site.id = :siteId " +
            "AND s.carrierStatus = com.mirai.inventoryservice.models.enums.CarrierStatus.DELIVERED " +
            "AND NOT EXISTS (SELECT 1 FROM ShipmentItem si WHERE si.shipment = s AND (si.receivedQuantity + si.damagedQuantity + si.displayQuantity + si.shopQuantity) > 0)")
    Page<Shipment> findAwaitingReceiptShipments(@Param("siteId") UUID siteId, Pageable pageable);

    @Query(value = "SELECT s FROM Shipment s " +
            "LEFT JOIN FETCH s.createdBy " +
            "LEFT JOIN FETCH s.receivedBy " +
            "WHERE s.status = com.mirai.inventoryservice.models.enums.ShipmentStatus.PENDING " +
            "AND s.site.id = :siteId " +
            "AND s.carrierStatus = com.mirai.inventoryservice.models.enums.CarrierStatus.DELIVERED " +
            "AND NOT EXISTS (SELECT 1 FROM ShipmentItem si WHERE si.shipment = s AND (si.receivedQuantity + si.damagedQuantity + si.displayQuantity + si.shopQuantity) > 0) " +
            "AND (LOWER(s.shipmentNumber) LIKE :searchPattern OR LOWER(s.supplierName) LIKE :searchPattern)",
            countQuery = "SELECT COUNT(s) FROM Shipment s " +
            "WHERE s.status = com.mirai.inventoryservice.models.enums.ShipmentStatus.PENDING " +
            "AND s.site.id = :siteId " +
            "AND s.carrierStatus = com.mirai.inventoryservice.models.enums.CarrierStatus.DELIVERED " +
            "AND NOT EXISTS (SELECT 1 FROM ShipmentItem si WHERE si.shipment = s AND (si.receivedQuantity + si.damagedQuantity + si.displayQuantity + si.shopQuantity) > 0) " +
            "AND (LOWER(s.shipmentNumber) LIKE :searchPattern OR LOWER(s.supplierName) LIKE :searchPattern)")
    Page<Shipment> findAwaitingReceiptShipmentsWithSearch(@Param("searchPattern") String searchPattern, @Param("siteId") UUID siteId, Pageable pageable);

    // PARTIAL: PENDING with at least one receipt
    @Query(value = "SELECT s FROM Shipment s " +
            "LEFT JOIN FETCH s.createdBy " +
            "LEFT JOIN FETCH s.receivedBy " +
            "WHERE s.status = com.mirai.inventoryservice.models.enums.ShipmentStatus.PENDING " +
            "AND s.site.id = :siteId " +
            "AND EXISTS (SELECT 1 FROM ShipmentItem si WHERE si.shipment = s AND (si.receivedQuantity + si.damagedQuantity + si.displayQuantity + si.shopQuantity) > 0)",
            countQuery = "SELECT COUNT(s) FROM Shipment s " +
            "WHERE s.status = com.mirai.inventoryservice.models.enums.ShipmentStatus.PENDING " +
            "AND s.site.id = :siteId " +
            "AND EXISTS (SELECT 1 FROM ShipmentItem si WHERE si.shipment = s AND (si.receivedQuantity + si.damagedQuantity + si.displayQuantity + si.shopQuantity) > 0)")
    Page<Shipment> findPartialShipments(@Param("siteId") UUID siteId, Pageable pageable);

    @Query(value = "SELECT s FROM Shipment s " +
            "LEFT JOIN FETCH s.createdBy " +
            "LEFT JOIN FETCH s.receivedBy " +
            "WHERE s.status = com.mirai.inventoryservice.models.enums.ShipmentStatus.PENDING " +
            "AND s.site.id = :siteId " +
            "AND EXISTS (SELECT 1 FROM ShipmentItem si WHERE si.shipment = s AND (si.receivedQuantity + si.damagedQuantity + si.displayQuantity + si.shopQuantity) > 0) " +
            "AND (LOWER(s.shipmentNumber) LIKE :searchPattern OR LOWER(s.supplierName) LIKE :searchPattern)",
            countQuery = "SELECT COUNT(s) FROM Shipment s " +
            "WHERE s.status = com.mirai.inventoryservice.models.enums.ShipmentStatus.PENDING " +
            "AND s.site.id = :siteId " +
            "AND EXISTS (SELECT 1 FROM ShipmentItem si WHERE si.shipment = s AND (si.receivedQuantity + si.damagedQuantity + si.displayQuantity + si.shopQuantity) > 0) " +
            "AND (LOWER(s.shipmentNumber) LIKE :searchPattern OR LOWER(s.supplierName) LIKE :searchPattern)")
    Page<Shipment> findPartialShipmentsWithSearch(@Param("searchPattern") String searchPattern, @Param("siteId") UUID siteId, Pageable pageable);

    // COMPLETED: status = RECEIVED
    @Query(value = "SELECT s FROM Shipment s " +
            "LEFT JOIN FETCH s.createdBy " +
            "LEFT JOIN FETCH s.receivedBy " +
            "WHERE s.status = :status AND s.site.id = :siteId",
            countQuery = "SELECT COUNT(s) FROM Shipment s WHERE s.status = :status AND s.site.id = :siteId")
    Page<Shipment> findCompletedShipments(@Param("status") ShipmentStatus status, @Param("siteId") UUID siteId, Pageable pageable);

    @Query(value = "SELECT s FROM Shipment s " +
            "LEFT JOIN FETCH s.createdBy " +
            "LEFT JOIN FETCH s.receivedBy " +
            "WHERE s.status = :status AND s.site.id = :siteId " +
            "AND (LOWER(s.shipmentNumber) LIKE :searchPattern OR LOWER(s.supplierName) LIKE :searchPattern)",
            countQuery = "SELECT COUNT(s) FROM Shipment s " +
            "WHERE s.status = :status AND s.site.id = :siteId " +
            "AND (LOWER(s.shipmentNumber) LIKE :searchPattern OR LOWER(s.supplierName) LIKE :searchPattern)")
    Page<Shipment> findCompletedShipmentsWithSearch(@Param("status") ShipmentStatus status, @Param("searchPattern") String searchPattern, @Param("siteId") UUID siteId, Pageable pageable);

    // FAILED: carrier_status = FAILED (regardless of inventory status)
    @Query(value = "SELECT s FROM Shipment s " +
            "LEFT JOIN FETCH s.createdBy " +
            "LEFT JOIN FETCH s.receivedBy " +
            "WHERE s.carrierStatus = com.mirai.inventoryservice.models.enums.CarrierStatus.FAILED " +
            "AND s.site.id = :siteId",
            countQuery = "SELECT COUNT(s) FROM Shipment s " +
            "WHERE s.carrierStatus = com.mirai.inventoryservice.models.enums.CarrierStatus.FAILED " +
            "AND s.site.id = :siteId")
    Page<Shipment> findFailedShipments(@Param("siteId") UUID siteId, Pageable pageable);

    @Query(value = "SELECT s FROM Shipment s " +
            "LEFT JOIN FETCH s.createdBy " +
            "LEFT JOIN FETCH s.receivedBy " +
            "WHERE s.carrierStatus = com.mirai.inventoryservice.models.enums.CarrierStatus.FAILED " +
            "AND s.site.id = :siteId " +
            "AND (LOWER(s.shipmentNumber) LIKE :searchPattern OR LOWER(s.supplierName) LIKE :searchPattern)",
            countQuery = "SELECT COUNT(s) FROM Shipment s " +
            "WHERE s.carrierStatus = com.mirai.inventoryservice.models.enums.CarrierStatus.FAILED " +
            "AND s.site.id = :siteId " +
            "AND (LOWER(s.shipmentNumber) LIKE :searchPattern OR LOWER(s.supplierName) LIKE :searchPattern)")
    Page<Shipment> findFailedShipmentsWithSearch(@Param("searchPattern") String searchPattern, @Param("siteId") UUID siteId, Pageable pageable);

    // Counts (mirror the page queries above), site-scoped
    @Query("SELECT COUNT(s) FROM Shipment s " +
            "WHERE s.status = com.mirai.inventoryservice.models.enums.ShipmentStatus.PENDING " +
            "AND s.site.id = :siteId " +
            "AND NOT EXISTS (SELECT 1 FROM ShipmentItem si WHERE si.shipment = s AND (si.receivedQuantity + si.damagedQuantity + si.displayQuantity + si.shopQuantity) > 0) " +
            "AND (s.carrierStatus IS NULL " +
            "     OR s.carrierStatus NOT IN (com.mirai.inventoryservice.models.enums.CarrierStatus.DELIVERED, com.mirai.inventoryservice.models.enums.CarrierStatus.FAILED))")
    long countActiveShipments(@Param("siteId") UUID siteId);

    @Query("SELECT COUNT(s) FROM Shipment s " +
            "WHERE s.status = com.mirai.inventoryservice.models.enums.ShipmentStatus.PENDING " +
            "AND s.site.id = :siteId " +
            "AND s.carrierStatus = com.mirai.inventoryservice.models.enums.CarrierStatus.DELIVERED " +
            "AND NOT EXISTS (SELECT 1 FROM ShipmentItem si WHERE si.shipment = s AND (si.receivedQuantity + si.damagedQuantity + si.displayQuantity + si.shopQuantity) > 0)")
    long countAwaitingReceiptShipments(@Param("siteId") UUID siteId);

    @Query("SELECT COUNT(s) FROM Shipment s " +
            "WHERE s.status = com.mirai.inventoryservice.models.enums.ShipmentStatus.PENDING " +
            "AND s.site.id = :siteId " +
            "AND EXISTS (SELECT 1 FROM ShipmentItem si WHERE si.shipment = s AND (si.receivedQuantity + si.damagedQuantity + si.displayQuantity + si.shopQuantity) > 0)")
    long countPartialShipments(@Param("siteId") UUID siteId);

    @Query("SELECT COUNT(s) FROM Shipment s WHERE s.status = :status AND s.site.id = :siteId")
    long countCompletedShipments(@Param("status") ShipmentStatus status, @Param("siteId") UUID siteId);

    @Query("SELECT COUNT(s) FROM Shipment s WHERE s.carrierStatus = com.mirai.inventoryservice.models.enums.CarrierStatus.FAILED AND s.site.id = :siteId")
    long countFailedShipments(@Param("siteId") UUID siteId);

    @Query("SELECT COUNT(s) FROM Shipment s " +
            "WHERE s.status = com.mirai.inventoryservice.models.enums.ShipmentStatus.PENDING " +
            "AND s.site.id = :siteId " +
            "AND s.expectedDeliveryDate IS NOT NULL " +
            "AND s.expectedDeliveryDate < CURRENT_DATE")
    long countOverdueShipments(@Param("siteId") UUID siteId);

    /**
     * Find the last received supplier for a product.
     * Returns [supplier_id, supplier_display_name] or null if no received shipments.
     * Filters to active suppliers only. Not site-scoped: called by
     * {@code ShipmentSupplierDeliveryHistoryAdapter} for catalog's preferred-supplier
     * auto-assignment, a supplier-identity decision rather than a shipment-data read, and out of
     * this slice's AC-2 scope (tracked separately, not a shipment list/detail exposure).
     */
    @Query(value = """
        SELECT s.id, s.display_name
        FROM shipments sh
        JOIN shipment_items si ON si.shipment_id = sh.id
        JOIN suppliers s ON s.id = sh.supplier_id
        WHERE si.item_id = :productId
          AND sh.status = 'RECEIVED'
          AND s.is_active = true
        ORDER BY sh.actual_delivery_date DESC NULLS LAST
        LIMIT 1
        """, nativeQuery = true)
    Object[] findLastDeliveredSupplierByProductId(@Param("productId") UUID productId);
}
