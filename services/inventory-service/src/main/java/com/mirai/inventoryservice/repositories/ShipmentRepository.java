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
    // Note: allocations are loaded via @BatchSize(size=50) to avoid MultipleBagFetchException
    @Query("SELECT s FROM Shipment s " +
            "LEFT JOIN FETCH s.createdBy " +
            "LEFT JOIN FETCH s.receivedBy " +
            "LEFT JOIN FETCH s.items si " +
            "LEFT JOIN FETCH si.item " +
            "WHERE s.id = :id")
    Optional<Shipment> findByIdWithAssociations(@Param("id") UUID id);

    // Non-paginated list with JOIN FETCH to avoid N+1 (for legacy API calls)
    @Query("SELECT DISTINCT s FROM Shipment s " +
            "LEFT JOIN FETCH s.createdBy " +
            "LEFT JOIN FETCH s.receivedBy " +
            "LEFT JOIN FETCH s.items si " +
            "LEFT JOIN FETCH si.item " +
            "ORDER BY s.createdAt DESC")
    List<Shipment> findAllWithAssociationsList();

    // Non-paginated list by status with JOIN FETCH
    @Query("SELECT DISTINCT s FROM Shipment s " +
            "LEFT JOIN FETCH s.createdBy " +
            "LEFT JOIN FETCH s.receivedBy " +
            "LEFT JOIN FETCH s.items si " +
            "LEFT JOIN FETCH si.item " +
            "WHERE s.status = :status " +
            "ORDER BY s.createdAt DESC")
    List<Shipment> findByStatusWithAssociationsList(@Param("status") ShipmentStatus status);

    // Paginated queries - fetch users eagerly, items loaded via @BatchSize(50)
    // NOTE: Do NOT use JOIN FETCH on collections (items) with pagination - causes in-memory pagination
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

    // Paginated query with optional status filter (no search)
    @Query(value = "SELECT s FROM Shipment s " +
            "LEFT JOIN FETCH s.createdBy " +
            "LEFT JOIN FETCH s.receivedBy " +
            "WHERE (:status IS NULL OR s.status = :status)",
            countQuery = "SELECT COUNT(s) FROM Shipment s WHERE (:status IS NULL OR s.status = :status)")
    Page<Shipment> findByStatusPaged(@Param("status") ShipmentStatus status, Pageable pageable);

    // Paginated query with status and search filters
    @Query(value = "SELECT s FROM Shipment s " +
            "LEFT JOIN FETCH s.createdBy " +
            "LEFT JOIN FETCH s.receivedBy " +
            "WHERE (:status IS NULL OR s.status = :status) " +
            "AND (LOWER(s.shipmentNumber) LIKE :searchPattern OR LOWER(s.supplierName) LIKE :searchPattern)",
            countQuery = "SELECT COUNT(s) FROM Shipment s " +
            "WHERE (:status IS NULL OR s.status = :status) " +
            "AND (LOWER(s.shipmentNumber) LIKE :searchPattern OR LOWER(s.supplierName) LIKE :searchPattern)")
    Page<Shipment> findByStatusAndSearch(@Param("status") ShipmentStatus status, @Param("searchPattern") String searchPattern, Pageable pageable);

    // Display status queries - ACTIVE: PENDING/IN_TRANSIT with no received items
    @Query(value = "SELECT s FROM Shipment s " +
            "LEFT JOIN FETCH s.createdBy " +
            "LEFT JOIN FETCH s.receivedBy " +
            "WHERE s.status IN :statuses " +
            "AND NOT EXISTS (SELECT 1 FROM ShipmentItem si WHERE si.shipment = s AND si.receivedQuantity > 0)",
            countQuery = "SELECT COUNT(s) FROM Shipment s " +
            "WHERE s.status IN :statuses " +
            "AND NOT EXISTS (SELECT 1 FROM ShipmentItem si WHERE si.shipment = s AND si.receivedQuantity > 0)")
    Page<Shipment> findActiveShipments(@Param("statuses") List<ShipmentStatus> statuses, Pageable pageable);

    @Query(value = "SELECT s FROM Shipment s " +
            "LEFT JOIN FETCH s.createdBy " +
            "LEFT JOIN FETCH s.receivedBy " +
            "WHERE s.status IN :statuses " +
            "AND NOT EXISTS (SELECT 1 FROM ShipmentItem si WHERE si.shipment = s AND si.receivedQuantity > 0) " +
            "AND (LOWER(s.shipmentNumber) LIKE :searchPattern OR LOWER(s.supplierName) LIKE :searchPattern)",
            countQuery = "SELECT COUNT(s) FROM Shipment s " +
            "WHERE s.status IN :statuses " +
            "AND NOT EXISTS (SELECT 1 FROM ShipmentItem si WHERE si.shipment = s AND si.receivedQuantity > 0) " +
            "AND (LOWER(s.shipmentNumber) LIKE :searchPattern OR LOWER(s.supplierName) LIKE :searchPattern)")
    Page<Shipment> findActiveShipmentsWithSearch(@Param("statuses") List<ShipmentStatus> statuses, @Param("searchPattern") String searchPattern, Pageable pageable);

    // Display status queries - PARTIAL: PENDING/IN_TRANSIT with some received items
    @Query(value = "SELECT s FROM Shipment s " +
            "LEFT JOIN FETCH s.createdBy " +
            "LEFT JOIN FETCH s.receivedBy " +
            "WHERE s.status IN :statuses " +
            "AND EXISTS (SELECT 1 FROM ShipmentItem si WHERE si.shipment = s AND si.receivedQuantity > 0)",
            countQuery = "SELECT COUNT(s) FROM Shipment s " +
            "WHERE s.status IN :statuses " +
            "AND EXISTS (SELECT 1 FROM ShipmentItem si WHERE si.shipment = s AND si.receivedQuantity > 0)")
    Page<Shipment> findPartialShipments(@Param("statuses") List<ShipmentStatus> statuses, Pageable pageable);

    @Query(value = "SELECT s FROM Shipment s " +
            "LEFT JOIN FETCH s.createdBy " +
            "LEFT JOIN FETCH s.receivedBy " +
            "WHERE s.status IN :statuses " +
            "AND EXISTS (SELECT 1 FROM ShipmentItem si WHERE si.shipment = s AND si.receivedQuantity > 0) " +
            "AND (LOWER(s.shipmentNumber) LIKE :searchPattern OR LOWER(s.supplierName) LIKE :searchPattern)",
            countQuery = "SELECT COUNT(s) FROM Shipment s " +
            "WHERE s.status IN :statuses " +
            "AND EXISTS (SELECT 1 FROM ShipmentItem si WHERE si.shipment = s AND si.receivedQuantity > 0) " +
            "AND (LOWER(s.shipmentNumber) LIKE :searchPattern OR LOWER(s.supplierName) LIKE :searchPattern)")
    Page<Shipment> findPartialShipmentsWithSearch(@Param("statuses") List<ShipmentStatus> statuses, @Param("searchPattern") String searchPattern, Pageable pageable);

    // Display status queries - COMPLETED: DELIVERED status
    @Query(value = "SELECT s FROM Shipment s " +
            "LEFT JOIN FETCH s.createdBy " +
            "LEFT JOIN FETCH s.receivedBy " +
            "WHERE s.status = :status",
            countQuery = "SELECT COUNT(s) FROM Shipment s " +
            "WHERE s.status = :status")
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

    // Count queries for each display status
    @Query("SELECT COUNT(s) FROM Shipment s " +
            "WHERE s.status IN :statuses " +
            "AND NOT EXISTS (SELECT 1 FROM ShipmentItem si WHERE si.shipment = s AND si.receivedQuantity > 0)")
    long countActiveShipments(@Param("statuses") List<ShipmentStatus> statuses);

    @Query("SELECT COUNT(s) FROM Shipment s " +
            "WHERE s.status IN :statuses " +
            "AND EXISTS (SELECT 1 FROM ShipmentItem si WHERE si.shipment = s AND si.receivedQuantity > 0)")
    long countPartialShipments(@Param("statuses") List<ShipmentStatus> statuses);

    @Query("SELECT COUNT(s) FROM Shipment s WHERE s.status = :status")
    long countCompletedShipments(@Param("status") ShipmentStatus status);
}
