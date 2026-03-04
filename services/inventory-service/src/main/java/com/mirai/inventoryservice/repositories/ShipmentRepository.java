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

    List<Shipment> findByStatusOrderByCreatedAtDesc(ShipmentStatus status);

    List<Shipment> findBySupplierNameContainingIgnoreCaseOrderByCreatedAtDesc(String supplierName);

    List<Shipment> findByOrderDateBetweenOrderByOrderDateDesc(LocalDate startDate, LocalDate endDate);

    List<Shipment> findByExpectedDeliveryDateBetweenOrderByExpectedDeliveryDateAsc(LocalDate startDate, LocalDate endDate);

    @Query("SELECT DISTINCT s FROM Shipment s JOIN s.items i WHERE i.item.id = :productId ORDER BY s.orderDate DESC")
    List<Shipment> findByItemsContainingProduct(@Param("productId") UUID productId);

    // Single shipment with JOIN FETCH to avoid N+1
    @Query("SELECT s FROM Shipment s " +
            "LEFT JOIN FETCH s.createdBy " +
            "LEFT JOIN FETCH s.receivedBy " +
            "LEFT JOIN FETCH s.items si " +
            "LEFT JOIN FETCH si.item " +
            "LEFT JOIN FETCH si.allocations " +
            "WHERE s.id = :id")
    Optional<Shipment> findByIdWithAssociations(@Param("id") UUID id);

    // Paginated queries with JOIN FETCH to avoid N+1
    @Query(value = "SELECT DISTINCT s FROM Shipment s " +
            "LEFT JOIN FETCH s.createdBy " +
            "LEFT JOIN FETCH s.receivedBy " +
            "LEFT JOIN FETCH s.items si " +
            "LEFT JOIN FETCH si.item " +
            "ORDER BY s.createdAt DESC",
            countQuery = "SELECT COUNT(DISTINCT s) FROM Shipment s")
    Page<Shipment> findAllWithAssociations(Pageable pageable);

    @Query(value = "SELECT DISTINCT s FROM Shipment s " +
            "LEFT JOIN FETCH s.createdBy " +
            "LEFT JOIN FETCH s.receivedBy " +
            "LEFT JOIN FETCH s.items si " +
            "LEFT JOIN FETCH si.item " +
            "WHERE s.status = :status " +
            "ORDER BY s.createdAt DESC",
            countQuery = "SELECT COUNT(DISTINCT s) FROM Shipment s WHERE s.status = :status")
    Page<Shipment> findByStatusWithAssociations(@Param("status") ShipmentStatus status, Pageable pageable);

    // Paginated query with optional status filter (no search)
    @Query(value = "SELECT DISTINCT s FROM Shipment s " +
            "LEFT JOIN FETCH s.createdBy " +
            "LEFT JOIN FETCH s.receivedBy " +
            "LEFT JOIN FETCH s.items si " +
            "LEFT JOIN FETCH si.item " +
            "WHERE (:status IS NULL OR s.status = :status) " +
            "ORDER BY s.createdAt DESC",
            countQuery = "SELECT COUNT(DISTINCT s) FROM Shipment s WHERE (:status IS NULL OR s.status = :status)")
    Page<Shipment> findByStatusPaged(@Param("status") ShipmentStatus status, Pageable pageable);

    // Paginated query with status and search filters
    @Query(value = "SELECT DISTINCT s FROM Shipment s " +
            "LEFT JOIN FETCH s.createdBy " +
            "LEFT JOIN FETCH s.receivedBy " +
            "LEFT JOIN FETCH s.items si " +
            "LEFT JOIN FETCH si.item " +
            "WHERE (:status IS NULL OR s.status = :status) " +
            "AND (LOWER(s.shipmentNumber) LIKE :searchPattern OR LOWER(s.supplierName) LIKE :searchPattern) " +
            "ORDER BY s.createdAt DESC",
            countQuery = "SELECT COUNT(DISTINCT s) FROM Shipment s " +
            "WHERE (:status IS NULL OR s.status = :status) " +
            "AND (LOWER(s.shipmentNumber) LIKE :searchPattern OR LOWER(s.supplierName) LIKE :searchPattern)")
    Page<Shipment> findByStatusAndSearch(@Param("status") ShipmentStatus status, @Param("searchPattern") String searchPattern, Pageable pageable);
}
