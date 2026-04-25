package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.enums.ShipmentStatus;
import com.mirai.inventoryservice.models.shipment.Shipment;
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
    Optional<Shipment> findByShipmentNumber(String shipmentNumber);

    Optional<Shipment> findByTrackingId(String trackingId);

    Optional<Shipment> findByEasypostTrackerId(String easypostTrackerId);

    List<Shipment> findByStatusOrderByCreatedAtDesc(ShipmentStatus status);

    List<Shipment> findBySupplierNameContainingIgnoreCaseOrderByCreatedAtDesc(String supplierName);

    List<Shipment> findByOrderDateBetweenOrderByOrderDateDesc(LocalDate startDate, LocalDate endDate);

    List<Shipment> findByExpectedDeliveryDateBetweenOrderByExpectedDeliveryDateAsc(LocalDate startDate, LocalDate endDate);

    @Query("SELECT DISTINCT s FROM Shipment s " +
            "LEFT JOIN FETCH s.createdBy " +
            "LEFT JOIN FETCH s.receivedBy " +
            "LEFT JOIN FETCH s.items si " +
            "LEFT JOIN FETCH si.item " +
            "WHERE si.item.id = :productId ORDER BY s.orderDate DESC")
    List<Shipment> findByItemsContainingProduct(@Param("productId") UUID productId);

    // Single shipment with JOIN FETCH to avoid N+1
    @Query("SELECT s FROM Shipment s " +
            "LEFT JOIN FETCH s.createdBy " +
            "LEFT JOIN FETCH s.receivedBy " +
            "LEFT JOIN FETCH s.items si " +
            "LEFT JOIN FETCH si.item " +
            "WHERE s.id = :id")
    Optional<Shipment> findByIdWithAssociations(@Param("id") UUID id);

    // Non-paginated list with JOIN FETCH to avoid N+1
    @Query("SELECT DISTINCT s FROM Shipment s " +
            "LEFT JOIN FETCH s.createdBy " +
            "LEFT JOIN FETCH s.receivedBy " +
            "LEFT JOIN FETCH s.items si " +
            "LEFT JOIN FETCH si.item " +
            "ORDER BY s.createdAt DESC")
    List<Shipment> findAllWithAssociationsList();

    @Query("SELECT DISTINCT s FROM Shipment s " +
            "LEFT JOIN FETCH s.createdBy " +
            "LEFT JOIN FETCH s.receivedBy " +
            "LEFT JOIN FETCH s.items si " +
            "LEFT JOIN FETCH si.item " +
            "WHERE s.status = :status " +
            "ORDER BY s.createdAt DESC")
    List<Shipment> findByStatusWithAssociationsList(@Param("status") ShipmentStatus status);

    @Query(value = "SELECT s FROM Shipment s " +
            "LEFT JOIN FETCH s.createdBy " +
            "LEFT JOIN FETCH s.receivedBy " +
            "ORDER BY s.createdAt DESC",
            countQuery = "SELECT COUNT(s) FROM Shipment s")
    Page<Shipment> findAllWithAssociations(Pageable pageable);

    @Query(value = "SELECT s FROM Shipment s " +
            "LEFT JOIN FETCH s.createdBy " +
            "LEFT JOIN FETCH s.receivedBy " +
            "WHERE s.status = :status " +
            "ORDER BY s.createdAt DESC",
            countQuery = "SELECT COUNT(s) FROM Shipment s WHERE s.status = :status")
    Page<Shipment> findByStatusWithAssociations(@Param("status") ShipmentStatus status, Pageable pageable);

    @Query(value = "SELECT s FROM Shipment s " +
            "LEFT JOIN FETCH s.createdBy " +
            "LEFT JOIN FETCH s.receivedBy " +
            "WHERE (:status IS NULL OR s.status = :status)",
            countQuery = "SELECT COUNT(s) FROM Shipment s WHERE (:status IS NULL OR s.status = :status)")
    Page<Shipment> findByStatusPaged(@Param("status") ShipmentStatus status, Pageable pageable);

    @Query(value = "SELECT s FROM Shipment s " +
            "LEFT JOIN FETCH s.createdBy " +
            "LEFT JOIN FETCH s.receivedBy " +
            "WHERE (:status IS NULL OR s.status = :status) " +
            "AND (LOWER(s.shipmentNumber) LIKE :searchPattern OR LOWER(s.supplierName) LIKE :searchPattern)",
            countQuery = "SELECT COUNT(s) FROM Shipment s " +
            "WHERE (:status IS NULL OR s.status = :status) " +
            "AND (LOWER(s.shipmentNumber) LIKE :searchPattern OR LOWER(s.supplierName) LIKE :searchPattern)")
    Page<Shipment> findByStatusAndSearch(@Param("status") ShipmentStatus status, @Param("searchPattern") String searchPattern, Pageable pageable);

    // ACTIVE: PENDING with no receipts and carrier_status NOT in (DELIVERED, FAILED)
    @Query(value = "SELECT s FROM Shipment s " +
            "LEFT JOIN FETCH s.createdBy " +
            "LEFT JOIN FETCH s.receivedBy " +
            "WHERE s.status = com.mirai.inventoryservice.models.enums.ShipmentStatus.PENDING " +
            "AND NOT EXISTS (SELECT 1 FROM ShipmentItem si WHERE si.shipment = s AND si.receivedQuantity > 0) " +
            "AND (s.carrierStatus IS NULL " +
            "     OR s.carrierStatus NOT IN (com.mirai.inventoryservice.models.enums.CarrierStatus.DELIVERED, com.mirai.inventoryservice.models.enums.CarrierStatus.FAILED))",
            countQuery = "SELECT COUNT(s) FROM Shipment s " +
            "WHERE s.status = com.mirai.inventoryservice.models.enums.ShipmentStatus.PENDING " +
            "AND NOT EXISTS (SELECT 1 FROM ShipmentItem si WHERE si.shipment = s AND si.receivedQuantity > 0) " +
            "AND (s.carrierStatus IS NULL " +
            "     OR s.carrierStatus NOT IN (com.mirai.inventoryservice.models.enums.CarrierStatus.DELIVERED, com.mirai.inventoryservice.models.enums.CarrierStatus.FAILED))")
    Page<Shipment> findActiveShipments(Pageable pageable);

    @Query(value = "SELECT s FROM Shipment s " +
            "LEFT JOIN FETCH s.createdBy " +
            "LEFT JOIN FETCH s.receivedBy " +
            "WHERE s.status = com.mirai.inventoryservice.models.enums.ShipmentStatus.PENDING " +
            "AND NOT EXISTS (SELECT 1 FROM ShipmentItem si WHERE si.shipment = s AND si.receivedQuantity > 0) " +
            "AND (s.carrierStatus IS NULL " +
            "     OR s.carrierStatus NOT IN (com.mirai.inventoryservice.models.enums.CarrierStatus.DELIVERED, com.mirai.inventoryservice.models.enums.CarrierStatus.FAILED)) " +
            "AND (LOWER(s.shipmentNumber) LIKE :searchPattern OR LOWER(s.supplierName) LIKE :searchPattern)",
            countQuery = "SELECT COUNT(s) FROM Shipment s " +
            "WHERE s.status = com.mirai.inventoryservice.models.enums.ShipmentStatus.PENDING " +
            "AND NOT EXISTS (SELECT 1 FROM ShipmentItem si WHERE si.shipment = s AND si.receivedQuantity > 0) " +
            "AND (s.carrierStatus IS NULL " +
            "     OR s.carrierStatus NOT IN (com.mirai.inventoryservice.models.enums.CarrierStatus.DELIVERED, com.mirai.inventoryservice.models.enums.CarrierStatus.FAILED)) " +
            "AND (LOWER(s.shipmentNumber) LIKE :searchPattern OR LOWER(s.supplierName) LIKE :searchPattern)")
    Page<Shipment> findActiveShipmentsWithSearch(@Param("searchPattern") String searchPattern, Pageable pageable);

    // AWAITING_RECEIPT: PENDING with no receipts and carrier_status = DELIVERED
    @Query(value = "SELECT s FROM Shipment s " +
            "LEFT JOIN FETCH s.createdBy " +
            "LEFT JOIN FETCH s.receivedBy " +
            "WHERE s.status = com.mirai.inventoryservice.models.enums.ShipmentStatus.PENDING " +
            "AND s.carrierStatus = com.mirai.inventoryservice.models.enums.CarrierStatus.DELIVERED " +
            "AND NOT EXISTS (SELECT 1 FROM ShipmentItem si WHERE si.shipment = s AND si.receivedQuantity > 0)",
            countQuery = "SELECT COUNT(s) FROM Shipment s " +
            "WHERE s.status = com.mirai.inventoryservice.models.enums.ShipmentStatus.PENDING " +
            "AND s.carrierStatus = com.mirai.inventoryservice.models.enums.CarrierStatus.DELIVERED " +
            "AND NOT EXISTS (SELECT 1 FROM ShipmentItem si WHERE si.shipment = s AND si.receivedQuantity > 0)")
    Page<Shipment> findAwaitingReceiptShipments(Pageable pageable);

    @Query(value = "SELECT s FROM Shipment s " +
            "LEFT JOIN FETCH s.createdBy " +
            "LEFT JOIN FETCH s.receivedBy " +
            "WHERE s.status = com.mirai.inventoryservice.models.enums.ShipmentStatus.PENDING " +
            "AND s.carrierStatus = com.mirai.inventoryservice.models.enums.CarrierStatus.DELIVERED " +
            "AND NOT EXISTS (SELECT 1 FROM ShipmentItem si WHERE si.shipment = s AND si.receivedQuantity > 0) " +
            "AND (LOWER(s.shipmentNumber) LIKE :searchPattern OR LOWER(s.supplierName) LIKE :searchPattern)",
            countQuery = "SELECT COUNT(s) FROM Shipment s " +
            "WHERE s.status = com.mirai.inventoryservice.models.enums.ShipmentStatus.PENDING " +
            "AND s.carrierStatus = com.mirai.inventoryservice.models.enums.CarrierStatus.DELIVERED " +
            "AND NOT EXISTS (SELECT 1 FROM ShipmentItem si WHERE si.shipment = s AND si.receivedQuantity > 0) " +
            "AND (LOWER(s.shipmentNumber) LIKE :searchPattern OR LOWER(s.supplierName) LIKE :searchPattern)")
    Page<Shipment> findAwaitingReceiptShipmentsWithSearch(@Param("searchPattern") String searchPattern, Pageable pageable);

    // PARTIAL: PENDING with at least one receipt
    @Query(value = "SELECT s FROM Shipment s " +
            "LEFT JOIN FETCH s.createdBy " +
            "LEFT JOIN FETCH s.receivedBy " +
            "WHERE s.status = com.mirai.inventoryservice.models.enums.ShipmentStatus.PENDING " +
            "AND EXISTS (SELECT 1 FROM ShipmentItem si WHERE si.shipment = s AND si.receivedQuantity > 0)",
            countQuery = "SELECT COUNT(s) FROM Shipment s " +
            "WHERE s.status = com.mirai.inventoryservice.models.enums.ShipmentStatus.PENDING " +
            "AND EXISTS (SELECT 1 FROM ShipmentItem si WHERE si.shipment = s AND si.receivedQuantity > 0)")
    Page<Shipment> findPartialShipments(Pageable pageable);

    @Query(value = "SELECT s FROM Shipment s " +
            "LEFT JOIN FETCH s.createdBy " +
            "LEFT JOIN FETCH s.receivedBy " +
            "WHERE s.status = com.mirai.inventoryservice.models.enums.ShipmentStatus.PENDING " +
            "AND EXISTS (SELECT 1 FROM ShipmentItem si WHERE si.shipment = s AND si.receivedQuantity > 0) " +
            "AND (LOWER(s.shipmentNumber) LIKE :searchPattern OR LOWER(s.supplierName) LIKE :searchPattern)",
            countQuery = "SELECT COUNT(s) FROM Shipment s " +
            "WHERE s.status = com.mirai.inventoryservice.models.enums.ShipmentStatus.PENDING " +
            "AND EXISTS (SELECT 1 FROM ShipmentItem si WHERE si.shipment = s AND si.receivedQuantity > 0) " +
            "AND (LOWER(s.shipmentNumber) LIKE :searchPattern OR LOWER(s.supplierName) LIKE :searchPattern)")
    Page<Shipment> findPartialShipmentsWithSearch(@Param("searchPattern") String searchPattern, Pageable pageable);

    // COMPLETED: status = RECEIVED
    @Query(value = "SELECT s FROM Shipment s " +
            "LEFT JOIN FETCH s.createdBy " +
            "LEFT JOIN FETCH s.receivedBy " +
            "WHERE s.status = :status",
            countQuery = "SELECT COUNT(s) FROM Shipment s WHERE s.status = :status")
    Page<Shipment> findCompletedShipments(@Param("status") ShipmentStatus status, Pageable pageable);

    @Query(value = "SELECT s FROM Shipment s " +
            "LEFT JOIN FETCH s.createdBy " +
            "LEFT JOIN FETCH s.receivedBy " +
            "WHERE s.status = :status " +
            "AND (LOWER(s.shipmentNumber) LIKE :searchPattern OR LOWER(s.supplierName) LIKE :searchPattern)",
            countQuery = "SELECT COUNT(s) FROM Shipment s " +
            "WHERE s.status = :status " +
            "AND (LOWER(s.shipmentNumber) LIKE :searchPattern OR LOWER(s.supplierName) LIKE :searchPattern)")
    Page<Shipment> findCompletedShipmentsWithSearch(@Param("status") ShipmentStatus status, @Param("searchPattern") String searchPattern, Pageable pageable);

    // FAILED: carrier_status = FAILED (regardless of inventory status)
    @Query(value = "SELECT s FROM Shipment s " +
            "LEFT JOIN FETCH s.createdBy " +
            "LEFT JOIN FETCH s.receivedBy " +
            "WHERE s.carrierStatus = com.mirai.inventoryservice.models.enums.CarrierStatus.FAILED",
            countQuery = "SELECT COUNT(s) FROM Shipment s " +
            "WHERE s.carrierStatus = com.mirai.inventoryservice.models.enums.CarrierStatus.FAILED")
    Page<Shipment> findFailedShipments(Pageable pageable);

    @Query(value = "SELECT s FROM Shipment s " +
            "LEFT JOIN FETCH s.createdBy " +
            "LEFT JOIN FETCH s.receivedBy " +
            "WHERE s.carrierStatus = com.mirai.inventoryservice.models.enums.CarrierStatus.FAILED " +
            "AND (LOWER(s.shipmentNumber) LIKE :searchPattern OR LOWER(s.supplierName) LIKE :searchPattern)",
            countQuery = "SELECT COUNT(s) FROM Shipment s " +
            "WHERE s.carrierStatus = com.mirai.inventoryservice.models.enums.CarrierStatus.FAILED " +
            "AND (LOWER(s.shipmentNumber) LIKE :searchPattern OR LOWER(s.supplierName) LIKE :searchPattern)")
    Page<Shipment> findFailedShipmentsWithSearch(@Param("searchPattern") String searchPattern, Pageable pageable);

    // Counts (mirror the page queries above)
    @Query("SELECT COUNT(s) FROM Shipment s " +
            "WHERE s.status = com.mirai.inventoryservice.models.enums.ShipmentStatus.PENDING " +
            "AND NOT EXISTS (SELECT 1 FROM ShipmentItem si WHERE si.shipment = s AND si.receivedQuantity > 0) " +
            "AND (s.carrierStatus IS NULL " +
            "     OR s.carrierStatus NOT IN (com.mirai.inventoryservice.models.enums.CarrierStatus.DELIVERED, com.mirai.inventoryservice.models.enums.CarrierStatus.FAILED))")
    long countActiveShipments();

    @Query("SELECT COUNT(s) FROM Shipment s " +
            "WHERE s.status = com.mirai.inventoryservice.models.enums.ShipmentStatus.PENDING " +
            "AND s.carrierStatus = com.mirai.inventoryservice.models.enums.CarrierStatus.DELIVERED " +
            "AND NOT EXISTS (SELECT 1 FROM ShipmentItem si WHERE si.shipment = s AND si.receivedQuantity > 0)")
    long countAwaitingReceiptShipments();

    @Query("SELECT COUNT(s) FROM Shipment s " +
            "WHERE s.status = com.mirai.inventoryservice.models.enums.ShipmentStatus.PENDING " +
            "AND EXISTS (SELECT 1 FROM ShipmentItem si WHERE si.shipment = s AND si.receivedQuantity > 0)")
    long countPartialShipments();

    @Query("SELECT COUNT(s) FROM Shipment s WHERE s.status = :status")
    long countCompletedShipments(@Param("status") ShipmentStatus status);

    @Query("SELECT COUNT(s) FROM Shipment s WHERE s.carrierStatus = com.mirai.inventoryservice.models.enums.CarrierStatus.FAILED")
    long countFailedShipments();

    @Query("SELECT COUNT(s) FROM Shipment s " +
            "WHERE s.status = com.mirai.inventoryservice.models.enums.ShipmentStatus.PENDING " +
            "AND s.expectedDeliveryDate IS NOT NULL " +
            "AND s.expectedDeliveryDate < CURRENT_DATE")
    long countOverdueShipments();

    /**
     * Find the last received supplier for a product.
     * Returns [supplier_id, supplier_display_name] or null if no received shipments.
     * Filters to active suppliers only.
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
