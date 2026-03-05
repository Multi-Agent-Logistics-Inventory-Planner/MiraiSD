package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.dtos.requests.SetMachineDisplayBatchRequestDTO;
import com.mirai.inventoryservice.dtos.requests.SetMachineDisplayRequestDTO;
import com.mirai.inventoryservice.dtos.requests.SwapMachineDisplayRequestDTO;
import com.mirai.inventoryservice.dtos.responses.MachineDisplayDTO;
import com.mirai.inventoryservice.models.MachineDisplay;
import com.mirai.inventoryservice.models.Product;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import com.mirai.inventoryservice.repositories.MachineDisplayRepository;
import com.mirai.inventoryservice.repositories.ProductRepository;
import com.mirai.inventoryservice.repositories.UserRepository;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MachineDisplayService {
    private final MachineDisplayRepository machineDisplayRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final EntityManager entityManager;
    private final AuditLogService auditLogService;

    @Value("${machine-display.stale-threshold-days:14}")
    private int staleThresholdDays;

    public MachineDisplayService(
            MachineDisplayRepository machineDisplayRepository,
            ProductRepository productRepository,
            UserRepository userRepository,
            EntityManager entityManager,
            AuditLogService auditLogService) {
        this.machineDisplayRepository = machineDisplayRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.entityManager = entityManager;
        this.auditLogService = auditLogService;
    }

    /**
     * Add a product to a machine's display.
     * Allows multiple products per machine (does not close existing displays).
     */
    @Transactional
    public MachineDisplay setDisplay(SetMachineDisplayRequestDTO request) {
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + request.getProductId()));

        List<MachineDisplay> existingDisplays = machineDisplayRepository
                .findActiveByLocationTypeAndMachineId(request.getLocationType(), request.getMachineId());

        boolean alreadyDisplayed = existingDisplays.stream()
                .anyMatch(d -> d.getProduct().getId().equals(request.getProductId()));

        if (alreadyDisplayed) {
            throw new IllegalArgumentException("Product is already displayed on this machine");
        }

        MachineDisplay newDisplay = MachineDisplay.builder()
                .locationType(request.getLocationType())
                .machineId(request.getMachineId())
                .product(product)
                .startedAt(OffsetDateTime.now())
                .actorId(request.getActorId())
                .build();

        MachineDisplay saved = machineDisplayRepository.save(newDisplay);

        String machineCode = resolveLocationCode(request.getMachineId(), request.getLocationType());
        auditLogService.createAuditLog(
                request.getActorId(),
                StockMovementReason.DISPLAY_SET,
                null, null,
                request.getMachineId(), machineCode,
                1, 0,
                product.getName(),
                null
        );

        return saved;
    }

    /**
     * Add multiple products to a machine's display in a single transaction.
     * Skips products that are already displayed (does not throw error).
     * Returns the list of newly created displays.
     */
    @Transactional
    public List<MachineDisplay> setDisplayBatch(SetMachineDisplayBatchRequestDTO request) {
        if (request.getProductIds().isEmpty()) {
            return Collections.emptyList();
        }

        // Get existing active displays for this machine (1 query)
        List<MachineDisplay> existingDisplays = machineDisplayRepository
                .findActiveByLocationTypeAndMachineId(request.getLocationType(), request.getMachineId());

        Set<UUID> existingProductIds = existingDisplays.stream()
                .map(d -> d.getProduct().getId())
                .collect(Collectors.toSet());

        // Filter out already displayed products and duplicates in request
        List<UUID> newProductIds = request.getProductIds().stream()
                .distinct()
                .filter(id -> !existingProductIds.contains(id))
                .collect(Collectors.toList());

        if (newProductIds.isEmpty()) {
            return Collections.emptyList();
        }

        // Batch fetch all products (1 query instead of N)
        Map<UUID, Product> productsById = productRepository.findAllById(newProductIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        // Validate all products exist
        for (UUID productId : newProductIds) {
            if (!productsById.containsKey(productId)) {
                throw new IllegalArgumentException("Product not found: " + productId);
            }
        }

        // Build all display entities
        OffsetDateTime now = OffsetDateTime.now();
        List<MachineDisplay> newDisplays = newProductIds.stream()
                .map(productId -> MachineDisplay.builder()
                        .locationType(request.getLocationType())
                        .machineId(request.getMachineId())
                        .product(productsById.get(productId))
                        .startedAt(now)
                        .actorId(request.getActorId())
                        .build())
                .collect(Collectors.toList());

        List<MachineDisplay> saved = machineDisplayRepository.saveAll(newDisplays);

        String machineCode = resolveLocationCode(request.getMachineId(), request.getLocationType());
        List<String> productNames = newProductIds.stream()
                .map(id -> productsById.get(id).getName())
                .collect(Collectors.toList());
        auditLogService.createAuditLog(
                request.getActorId(),
                StockMovementReason.DISPLAY_SET,
                null, null,
                request.getMachineId(), machineCode,
                productNames.size(), 0,
                buildProductSummary(productNames),
                null
        );

        return saved;
    }

    /**
     * Clear all displays for a machine (end all active displays)
     */
    @Transactional
    public void clearDisplay(LocationType locationType, UUID machineId, UUID actorId) {
        List<MachineDisplay> existingDisplays = machineDisplayRepository
                .findActiveByLocationTypeAndMachineId(locationType, machineId);

        if (existingDisplays.isEmpty()) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();
        existingDisplays.forEach(display -> display.setEndedAt(now));
        machineDisplayRepository.saveAll(existingDisplays);

        String machineCode = resolveLocationCode(machineId, locationType);
        List<String> productNames = existingDisplays.stream()
                .map(d -> d.getProduct().getName())
                .collect(Collectors.toList());
        auditLogService.createAuditLog(
                actorId,
                StockMovementReason.DISPLAY_REMOVED,
                machineId, machineCode,
                null, null,
                productNames.size(), 0,
                buildProductSummary(productNames),
                null
        );
    }

    /**
     * Clear a specific display by ID
     */
    @Transactional
    public void clearDisplayById(UUID displayId, UUID actorId) {
        MachineDisplay display = machineDisplayRepository.findByIdWithProduct(displayId)
                .orElseThrow(() -> new IllegalArgumentException("Display not found: " + displayId));

        if (display.getEndedAt() != null) {
            throw new IllegalArgumentException("Display is already ended");
        }

        display.setEndedAt(OffsetDateTime.now());
        machineDisplayRepository.save(display);

        String machineCode = resolveLocationCode(display.getMachineId(), display.getLocationType());
        auditLogService.createAuditLog(
                actorId,
                StockMovementReason.DISPLAY_REMOVED,
                display.getMachineId(), machineCode,
                null, null,
                1, 0,
                display.getProduct().getName(),
                null
        );
    }

    /**
     * Atomically swap one displayed product for another on the same machine.
     * Ends the outgoing display record and creates a new one for the incoming product.
     */
    @Transactional
    public List<MachineDisplayDTO> swapDisplay(SwapMachineDisplayRequestDTO request) {
        MachineDisplay outgoing = machineDisplayRepository.findByIdWithProduct(request.getOutgoingDisplayId())
                .orElseThrow(() -> new IllegalArgumentException("Display not found: " + request.getOutgoingDisplayId()));

        if (outgoing.getEndedAt() != null) {
            throw new IllegalArgumentException("Display is already ended");
        }

        String outgoingProductName = outgoing.getProduct().getName();

        outgoing.setEndedAt(OffsetDateTime.now());
        machineDisplayRepository.save(outgoing);

        Product incoming = productRepository.findById(request.getIncomingProductId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + request.getIncomingProductId()));

        List<MachineDisplay> currentDisplays = machineDisplayRepository
                .findActiveByLocationTypeAndMachineId(request.getLocationType(), request.getMachineId());

        boolean alreadyDisplayed = currentDisplays.stream()
                .anyMatch(d -> d.getProduct().getId().equals(request.getIncomingProductId()));

        if (alreadyDisplayed) {
            throw new IllegalArgumentException("Product is already displayed on this machine");
        }

        MachineDisplay newDisplay = MachineDisplay.builder()
                .locationType(request.getLocationType())
                .machineId(request.getMachineId())
                .product(incoming)
                .startedAt(OffsetDateTime.now())
                .actorId(request.getActorId())
                .build();
        machineDisplayRepository.save(newDisplay);

        String machineCode = resolveLocationCode(request.getMachineId(), request.getLocationType());
        String productSummary = outgoingProductName + " → " + incoming.getName();
        auditLogService.createAuditLog(
                request.getActorId(),
                StockMovementReason.DISPLAY_SWAP,
                request.getMachineId(), machineCode,
                request.getMachineId(), machineCode,
                1, 0,
                productSummary,
                null
        );

        return getActiveDisplaysForMachine(request.getLocationType(), request.getMachineId());
    }

    /**
     * Get all active displays for a specific machine
     */
    public List<MachineDisplayDTO> getActiveDisplaysForMachine(LocationType locationType, UUID machineId) {
        List<MachineDisplay> displays = machineDisplayRepository
                .findActiveByLocationTypeAndMachineId(locationType, machineId);
        return toDTOList(displays);
    }

    /**
     * Get current display for a specific machine
     */
    public Optional<MachineDisplayDTO> getCurrentDisplay(LocationType locationType, UUID machineId) {
        return machineDisplayRepository
                .findByLocationTypeAndMachineIdAndEndedAtIsNull(locationType, machineId)
                .map(display -> toDTO(display));
    }

    /**
     * Get all current active displays
     */
    public List<MachineDisplayDTO> getAllActiveDisplays() {
        List<MachineDisplay> displays = machineDisplayRepository.findByEndedAtIsNullOrderByStartedAtAsc();
        return toDTOList(displays);
    }

    /**
     * Get all current active displays with pagination
     */
    public Page<MachineDisplayDTO> getAllActiveDisplays(Pageable pageable) {
        Page<MachineDisplay> displays = machineDisplayRepository.findByEndedAtIsNull(pageable);
        return toDTOPage(displays);
    }

    /**
     * Get active displays for a specific location type
     */
    public List<MachineDisplayDTO> getActiveDisplaysByLocationType(LocationType locationType) {
        List<MachineDisplay> displays = machineDisplayRepository
                .findByLocationTypeAndEndedAtIsNullOrderByStartedAtAsc(locationType);
        return toDTOList(displays);
    }

    /**
     * Get display history for a specific machine
     */
    public List<MachineDisplayDTO> getMachineHistory(LocationType locationType, UUID machineId) {
        List<MachineDisplay> displays = machineDisplayRepository
                .findByLocationTypeAndMachineIdOrderByStartedAtDesc(locationType, machineId);
        return toDTOList(displays);
    }

    /**
     * Get display history for a specific machine with pagination
     */
    public Page<MachineDisplayDTO> getMachineHistory(LocationType locationType, UUID machineId, Pageable pageable) {
        Page<MachineDisplay> displays = machineDisplayRepository
                .findByLocationTypeAndMachineIdOrderByStartedAtDesc(locationType, machineId, pageable);
        return toDTOPage(displays);
    }

    /**
     * Get all stale displays (active longer than threshold)
     */
    public List<MachineDisplayDTO> getStaleDisplays() {
        OffsetDateTime threshold = OffsetDateTime.now().minusDays(staleThresholdDays);
        List<MachineDisplay> displays = machineDisplayRepository.findStaleDisplays(threshold);
        return toDTOList(displays);
    }

    /**
     * Get stale displays for a specific location type
     */
    public List<MachineDisplayDTO> getStaleDisplaysByLocationType(LocationType locationType) {
        OffsetDateTime threshold = OffsetDateTime.now().minusDays(staleThresholdDays);
        List<MachineDisplay> displays = machineDisplayRepository
                .findStaleDisplaysByLocationType(locationType, threshold);
        return toDTOList(displays);
    }

    /**
     * Get stale displays with custom threshold
     */
    public List<MachineDisplayDTO> getStaleDisplays(int thresholdDays) {
        OffsetDateTime threshold = OffsetDateTime.now().minusDays(thresholdDays);
        List<MachineDisplay> displays = machineDisplayRepository.findStaleDisplays(threshold);
        return toDTOList(displays);
    }

    /**
     * Get display history for a product
     */
    public List<MachineDisplayDTO> getProductHistory(UUID productId) {
        List<MachineDisplay> displays = machineDisplayRepository.findByProduct_IdOrderByStartedAtDesc(productId);
        return toDTOList(displays);
    }

    // ========= Helper Methods =========

    private MachineDisplayDTO toDTO(MachineDisplay display) {
        String machineCode = resolveLocationCode(display.getMachineId(), display.getLocationType());
        String actorName = resolveActorName(display.getActorId());

        return MachineDisplayDTO.fromEntity(display, machineCode, actorName, staleThresholdDays);
    }

    private List<MachineDisplayDTO> toDTOList(List<MachineDisplay> displays) {
        if (displays.isEmpty()) {
            return Collections.emptyList();
        }

        // Batch fetch actor names to avoid N+1
        Set<UUID> actorIds = displays.stream()
                .map(MachineDisplay::getActorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, String> actorNames = batchResolveActorNames(actorIds);

        // Batch fetch machine codes
        Map<String, String> machineCodes = batchResolveMachineCodes(displays);

        return displays.stream()
                .map(display -> {
                    String key = display.getLocationType() + ":" + display.getMachineId();
                    String machineCode = machineCodes.get(key);
                    String actorName = display.getActorId() != null ? actorNames.get(display.getActorId()) : null;

                    return MachineDisplayDTO.fromEntity(display, machineCode, actorName, staleThresholdDays);
                })
                .collect(Collectors.toList());
    }

    private Page<MachineDisplayDTO> toDTOPage(Page<MachineDisplay> displays) {
        if (displays.isEmpty()) {
            return displays.map(d -> null); // Returns empty page with same metadata
        }

        List<MachineDisplay> content = displays.getContent();

        // Batch fetch actor names to avoid N+1
        Set<UUID> actorIds = content.stream()
                .map(MachineDisplay::getActorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, String> actorNames = batchResolveActorNames(actorIds);

        // Batch fetch machine codes
        Map<String, String> machineCodes = batchResolveMachineCodes(content);

        // Map content with pre-fetched data
        List<MachineDisplayDTO> dtoContent = content.stream()
                .map(display -> {
                    String key = display.getLocationType() + ":" + display.getMachineId();
                    String machineCode = machineCodes.get(key);
                    String actorName = display.getActorId() != null ? actorNames.get(display.getActorId()) : null;

                    return MachineDisplayDTO.fromEntity(display, machineCode, actorName, staleThresholdDays);
                })
                .collect(Collectors.toList());

        return new org.springframework.data.domain.PageImpl<>(
                dtoContent,
                displays.getPageable(),
                displays.getTotalElements()
        );
    }

    private String resolveLocationCode(UUID locationId, LocationType locationType) {
        if (locationId == null) return null;

        Object result = entityManager.createNativeQuery("SELECT resolve_location_code(:locationId, :locationType)")
                .setParameter("locationId", locationId)
                .setParameter("locationType", locationType.name())
                .getSingleResult();

        return result != null ? result.toString() : null;
    }

    private String resolveActorName(UUID actorId) {
        if (actorId == null) return null;

        return userRepository.findById(actorId)
                .map(user -> user.getFullName())
                .orElse(null);
    }

    private Map<UUID, String> batchResolveActorNames(Set<UUID> actorIds) {
        if (actorIds.isEmpty()) {
            return Collections.emptyMap();
        }

        return userRepository.findAllById(actorIds).stream()
                .collect(Collectors.toMap(
                        user -> user.getId(),
                        user -> user.getFullName()
                ));
    }

    private Map<String, String> batchResolveMachineCodes(List<MachineDisplay> displays) {
        Map<String, String> result = new HashMap<>();

        // Group by location type
        Map<LocationType, Set<UUID>> machineIdsByType = displays.stream()
                .collect(Collectors.groupingBy(
                        MachineDisplay::getLocationType,
                        Collectors.mapping(MachineDisplay::getMachineId, Collectors.toSet())
                ));

        // Batch fetch codes per location type (1 query per type instead of N queries per machine)
        for (Map.Entry<LocationType, Set<UUID>> entry : machineIdsByType.entrySet()) {
            LocationType locationType = entry.getKey();
            Set<UUID> machineIds = entry.getValue();

            if (machineIds.isEmpty()) continue;

            String tableName = getTableNameForLocationType(locationType);
            String codeColumn = getCodeColumnForLocationType(locationType);

            if (tableName == null || codeColumn == null) {
                // Handle NOT_ASSIGNED or unknown types
                for (UUID machineId : machineIds) {
                    result.put(locationType + ":" + machineId, "NA");
                }
                continue;
            }

            // Single query to fetch all codes for this location type
            @SuppressWarnings("unchecked")
            List<Object[]> rows = entityManager.createNativeQuery(
                    "SELECT id, " + codeColumn + " FROM " + tableName + " WHERE id IN (:ids)")
                    .setParameter("ids", machineIds)
                    .getResultList();

            for (Object[] row : rows) {
                UUID id = (UUID) row[0];
                String code = (String) row[1];
                result.put(locationType + ":" + id, code);
            }
        }

        return result;
    }

    private String getTableNameForLocationType(LocationType locationType) {
        return switch (locationType) {
            case BOX_BIN -> "box_bins";
            case SINGLE_CLAW_MACHINE -> "single_claw_machines";
            case DOUBLE_CLAW_MACHINE -> "double_claw_machines";
            case KEYCHAIN_MACHINE -> "keychain_machines";
            case CABINET -> "cabinets";
            case RACK -> "racks";
            case FOUR_CORNER_MACHINE -> "four_corner_machines";
            case PUSHER_MACHINE -> "pusher_machines";
            case NOT_ASSIGNED -> null;
        };
    }

    private String buildProductSummary(List<String> productNames) {
        if (productNames.isEmpty()) return null;
        if (productNames.size() == 1) return productNames.get(0);
        if (productNames.size() == 2) return productNames.get(0) + " + " + productNames.get(1);
        return productNames.get(0) + " + " + (productNames.size() - 1) + " more";
    }

    private String getCodeColumnForLocationType(LocationType locationType) {
        return switch (locationType) {
            case BOX_BIN -> "box_bin_code";
            case SINGLE_CLAW_MACHINE -> "single_claw_machine_code";
            case DOUBLE_CLAW_MACHINE -> "double_claw_machine_code";
            case KEYCHAIN_MACHINE -> "keychain_machine_code";
            case CABINET -> "cabinet_code";
            case RACK -> "rack_code";
            case FOUR_CORNER_MACHINE -> "four_corner_machine_code";
            case PUSHER_MACHINE -> "pusher_machine_code";
            case NOT_ASSIGNED -> null;
        };
    }
}
