package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.dtos.mappers.ProductMapper;
import com.mirai.inventoryservice.dtos.responses.ProductResponseDTO;
import com.mirai.inventoryservice.dtos.responses.SupplierResponseDTO;
import com.mirai.inventoryservice.exceptions.SupplierNotFoundException;
import com.mirai.inventoryservice.models.Product;
import com.mirai.inventoryservice.models.Supplier;
import com.mirai.inventoryservice.repositories.ProductRepository;
import com.mirai.inventoryservice.repositories.SupplierRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Transactional
public class SupplierService {
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    public SupplierService(SupplierRepository supplierRepository, ProductRepository productRepository, ProductMapper productMapper) {
        this.supplierRepository = supplierRepository;
        this.productRepository = productRepository;
        this.productMapper = productMapper;
    }

    /**
     * Resolve or create a supplier by display name.
     * Thread-safe via database upsert with ON CONFLICT.
     */
    public Supplier resolveOrCreate(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return null;
        }

        String trimmedName = displayName.trim();
        UUID id = supplierRepository.upsertByDisplayName(trimmedName);
        return supplierRepository.findById(id).orElse(null);
    }

    /**
     * Get supplier by ID.
     */
    public Supplier getSupplierById(UUID id) {
        return supplierRepository.findById(id)
                .orElseThrow(() -> new SupplierNotFoundException("Supplier not found with id: " + id));
    }

    /**
     * Get supplier by ID with lead time stats as DTO.
     */
    public SupplierResponseDTO getSupplierWithStats(UUID id) {
        Supplier supplier = getSupplierById(id);
        return toResponseDTOWithStats(supplier);
    }

    /**
     * Get all suppliers with their lead time statistics.
     */
    public List<SupplierResponseDTO> getAllSuppliersWithStats() {
        return supplierRepository.findAllWithStats().stream()
                .map(this::objectArrayToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get suppliers filtered by active status with stats.
     */
    public List<SupplierResponseDTO> getSuppliersByActiveWithStats(boolean active) {
        return supplierRepository.findAllWithStatsByActive(active).stream()
                .map(this::objectArrayToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Search suppliers by name.
     */
    public List<SupplierResponseDTO> searchSuppliers(String query, Boolean activeOnly) {
        List<Supplier> suppliers;
        if (activeOnly != null && activeOnly) {
            suppliers = supplierRepository.searchActiveByDisplayName(query);
        } else {
            suppliers = supplierRepository.searchByDisplayName(query);
        }
        return suppliers.stream()
                .map(this::toResponseDTO)
                .collect(Collectors.toList());
    }

    /**
     * Create a new supplier.
     */
    public SupplierResponseDTO createSupplier(String displayName, String contactEmail) {
        Supplier supplier = Supplier.builder()
                .displayName(displayName.trim())
                .contactEmail(contactEmail)
                .isActive(true)
                .build();

        supplier = supplierRepository.save(supplier);
        return toResponseDTOWithStats(supplier);
    }

    /**
     * Update supplier details.
     */
    public SupplierResponseDTO updateSupplier(UUID id, String displayName, String contactEmail, Boolean isActive) {
        Supplier supplier = getSupplierById(id);

        if (displayName != null && !displayName.isBlank()) {
            supplier.setDisplayName(displayName.trim());
        }
        if (contactEmail != null) {
            supplier.setContactEmail(contactEmail.isBlank() ? null : contactEmail);
        }
        if (isActive != null) {
            supplier.setIsActive(isActive);
        }

        supplier = supplierRepository.save(supplier);
        return toResponseDTOWithStats(supplier);
    }

    /**
     * Soft delete (deactivate) a supplier.
     */
    public void deactivateSupplier(UUID id) {
        Supplier supplier = getSupplierById(id);
        supplier.setIsActive(false);
        supplierRepository.save(supplier);
    }

    /**
     * Reactivate a supplier.
     */
    public void activateSupplier(UUID id) {
        Supplier supplier = getSupplierById(id);
        supplier.setIsActive(true);
        supplierRepository.save(supplier);
    }

    /**
     * Assign products to a supplier (sets preferred_supplier_id).
     * Returns the number of products updated.
     */
    public int assignProductsToSupplier(UUID supplierId, List<UUID> productIds) {
        Supplier supplier = getSupplierById(supplierId);
        List<Product> products = productRepository.findAllById(productIds);

        for (Product product : products) {
            product.setPreferredSupplier(supplier);
            product.setPreferredSupplierAuto(false); // Manual assignment
        }

        productRepository.saveAll(products);
        return products.size();
    }

    /**
     * Get all products assigned to a supplier.
     */
    public List<ProductResponseDTO> getProductsBySupplierId(UUID supplierId) {
        // Verify supplier exists
        getSupplierById(supplierId);
        List<Product> products = productRepository.findByPreferredSupplierIdWithCategories(supplierId);
        return productMapper.toResponseDTOList(products);
    }

    /**
     * Get all active suppliers (for autocomplete).
     */
    public List<SupplierResponseDTO> getActiveSuppliers() {
        return supplierRepository.findByIsActiveTrueOrderByDisplayNameAsc().stream()
                .map(this::toResponseDTO)
                .collect(Collectors.toList());
    }

    /**
     * Canonicalize a supplier name (for comparison).
     * Matches the database function: NFKC normalize -> lowercase -> trim -> collapse whitespace.
     */
    public String canonicalize(String name) {
        if (name == null) {
            return null;
        }
        String normalized = Normalizer.normalize(name.trim(), Normalizer.Form.NFKC);
        String collapsed = WHITESPACE.matcher(normalized).replaceAll(" ");
        return collapsed.toLowerCase(Locale.ROOT);
    }

    private SupplierResponseDTO toResponseDTO(Supplier supplier) {
        return SupplierResponseDTO.builder()
                .id(supplier.getId())
                .displayName(supplier.getDisplayName())
                .contactEmail(supplier.getContactEmail())
                .isActive(supplier.getIsActive())
                .createdAt(supplier.getCreatedAt())
                .build();
    }

    private SupplierResponseDTO toResponseDTOWithStats(Supplier supplier) {
        SupplierResponseDTO dto = toResponseDTO(supplier);

        // Get lead time stats from MV
        Object[] stats = supplierRepository.getSupplierLeadTimeStats(supplier.getId());
        if (stats != null && stats.length >= 3 && stats[0] != null) {
            dto.setAvgLeadTimeDays(toBigDecimal(stats[0]));
            dto.setSigmaL(toBigDecimal(stats[1]));
            dto.setShipmentCount(toLong(stats[2]));
        } else {
            // No stats in MV, count shipments directly
            dto.setShipmentCount(supplierRepository.countShipmentsBySupplierId(supplier.getId()));
        }

        // Get product count
        dto.setProductCount(productRepository.countByPreferredSupplierId(supplier.getId()));

        return dto;
    }

    private SupplierResponseDTO objectArrayToDTO(Object[] row) {
        return SupplierResponseDTO.builder()
                .id((UUID) row[0])
                .displayName((String) row[1])
                .contactEmail((String) row[2])
                .isActive((Boolean) row[3])
                .createdAt(toOffsetDateTime(row[4]))
                .shipmentCount(toLong(row[5]))
                .avgLeadTimeDays(toBigDecimal(row[6]))
                .sigmaL(toBigDecimal(row[7]))
                .productCount(toLong(row[8]))
                .build();
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        return null;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0L;
    }

    private OffsetDateTime toOffsetDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof OffsetDateTime) {
            return (OffsetDateTime) value;
        }
        if (value instanceof java.time.Instant) {
            return ((java.time.Instant) value).atOffset(java.time.ZoneOffset.UTC);
        }
        if (value instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) value).toInstant().atOffset(java.time.ZoneOffset.UTC);
        }
        return null;
    }
}
