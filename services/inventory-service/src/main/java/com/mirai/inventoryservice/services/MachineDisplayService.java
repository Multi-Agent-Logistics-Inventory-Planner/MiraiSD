package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.dtos.requests.SetMachineDisplayBatchRequestDTO;
import com.mirai.inventoryservice.dtos.requests.SetMachineDisplayRequestDTO;
import com.mirai.inventoryservice.dtos.responses.MachineDisplayDTO;
import com.mirai.inventoryservice.models.MachineDisplay;
import com.mirai.inventoryservice.models.Product;
import com.mirai.inventoryservice.models.enums.LocationType;
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

    @Value("${machine-display.stale-threshold-days:14}")
    private int staleThresholdDays;

    public MachineDisplayService(
            MachineDisplayRepository machineDisplayRepository,
            ProductRepository productRepository,
            UserRepository userRepository,
            EntityManager entityManager) {
        this.machineDisplayRepository = machineDisplayRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.entityManager = entityManager;
    }

    /**
     * Add a product to a machine's display.
     * Allows multiple products per machine (does not close existing displays).
     */
    @Transactional
    public MachineDisplay setDisplay(SetMachineDisplayRequestDTO request) {
        // Validate product exists
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + request.getProductId()));

        // Check if this product is already displayed on this machine
        List<MachineDisplay> existingDisplays = machineDisplayRepository
                .findActiveByLocationTypeAndMachineId(request.getLocationType(), request.getMachineId());

        boolean alreadyDisplayed = existingDisplays.stream()
                .anyMatch(d -> d.getProduct().getId().equals(request.getProductId()));

        if (alreadyDisplayed) {
            throw new IllegalArgumentException("Product is already displayed on this machine");
        }

        // Create new display record (allows multiple products per machine)
        MachineDisplay newDisplay = MachineDisplay.builder()
                .locationType(request.getLocationType())
                .machineId(request.getMachineId())
                .product(product)
                .startedAt(OffsetDateTime.now())
                .actorId(request.getActorId())
                .build();

        return machineDisplayRepository.save(newDisplay);
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

        // Batch save all displays (1 batch insert instead of N)
        return machineDisplayRepository.saveAll(newDisplays);
    }

    /**
     * Clear all displays for a machine (end all active displays)
     */
    @Transactional
    public void clearDisplay(LocationType locationType, UUID machineId, UUID actorId) {
        List<MachineDisplay> existingDisplays = machineDisplayRepository
                .findActiveByLocationTypeAndMachineId(locationType, machineId);

        for (MachineDisplay display : existingDisplays) {
            display.setEndedAt(OffsetDateTime.now());
            machineDisplayRepository.save(display);
        }
    }

    /**
     * Clear a specific display by ID
     */
    @Transactional
    public void clearDisplayById(UUID displayId, UUID actorId) {
        MachineDisplay display = machineDisplayRepository.findById(displayId)
                .orElseThrow(() -> new IllegalArgumentException("Display not found: " + displayId));

        if (display.getEndedAt() != null) {
            throw new IllegalArgumentException("Display is already ended");
        }

        display.setEndedAt(OffsetDateTime.now());
        machineDisplayRepository.save(display);
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
        return displays.map(this::toDTO);
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
        return displays.map(this::toDTO);
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

        for (Map.Entry<LocationType, Set<UUID>> entry : machineIdsByType.entrySet()) {
            LocationType locationType = entry.getKey();
            Set<UUID> machineIds = entry.getValue();

            for (UUID machineId : machineIds) {
                String code = resolveLocationCode(machineId, locationType);
                result.put(locationType + ":" + machineId, code);
            }
        }

        return result;
    }
}
