package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.dtos.responses.InventoryTotalDTO;
import com.mirai.inventoryservice.dtos.responses.ProductInventoryEntryDTO;
import com.mirai.inventoryservice.dtos.responses.ProductInventoryResponseDTO;
import com.mirai.inventoryservice.exceptions.ProductNotFoundException;
import com.mirai.inventoryservice.models.Product;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.models.inventory.*;
import com.mirai.inventoryservice.repositories.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.DisposableBean;

/**
 * Service for fetching aggregated inventory data across all location types.
 * Provides optimized endpoints to reduce N+1 query problems when looking up
 * inventory by product.
 */
@Service
public class InventoryAggregateService implements DisposableBean {

    private final ProductRepository productRepository;
    private final BoxBinInventoryRepository boxBinInventoryRepository;
    private final RackInventoryRepository rackInventoryRepository;
    private final CabinetInventoryRepository cabinetInventoryRepository;
    private final SingleClawMachineInventoryRepository singleClawMachineInventoryRepository;
    private final DoubleClawMachineInventoryRepository doubleClawMachineInventoryRepository;
    private final FourCornerMachineInventoryRepository fourCornerMachineInventoryRepository;
    private final PusherMachineInventoryRepository pusherMachineInventoryRepository;
    private final WindowInventoryRepository windowInventoryRepository;
    private final NotAssignedInventoryRepository notAssignedInventoryRepository;

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public InventoryAggregateService(
            ProductRepository productRepository,
            BoxBinInventoryRepository boxBinInventoryRepository,
            RackInventoryRepository rackInventoryRepository,
            CabinetInventoryRepository cabinetInventoryRepository,
            SingleClawMachineInventoryRepository singleClawMachineInventoryRepository,
            DoubleClawMachineInventoryRepository doubleClawMachineInventoryRepository,
            FourCornerMachineInventoryRepository fourCornerMachineInventoryRepository,
            PusherMachineInventoryRepository pusherMachineInventoryRepository,
            WindowInventoryRepository windowInventoryRepository,
            NotAssignedInventoryRepository notAssignedInventoryRepository) {
        this.productRepository = productRepository;
        this.boxBinInventoryRepository = boxBinInventoryRepository;
        this.rackInventoryRepository = rackInventoryRepository;
        this.cabinetInventoryRepository = cabinetInventoryRepository;
        this.singleClawMachineInventoryRepository = singleClawMachineInventoryRepository;
        this.doubleClawMachineInventoryRepository = doubleClawMachineInventoryRepository;
        this.fourCornerMachineInventoryRepository = fourCornerMachineInventoryRepository;
        this.pusherMachineInventoryRepository = pusherMachineInventoryRepository;
        this.windowInventoryRepository = windowInventoryRepository;
        this.notAssignedInventoryRepository = notAssignedInventoryRepository;
    }

    @Override
    public void destroy() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Get all inventory entries for a specific product across all location types.
     * Queries all 9 inventory tables in parallel for optimal performance.
     *
     * @param productId The product ID to look up
     * @return ProductInventoryResponseDTO containing all inventory entries
     */
    public ProductInventoryResponseDTO getInventoryByProduct(UUID productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Product not found: " + productId));

        List<ProductInventoryEntryDTO> entries = fetchAllInventoryEntries(productId);

        int totalQuantity = entries.stream()
                .mapToInt(ProductInventoryEntryDTO::getQuantity)
                .sum();

        return ProductInventoryResponseDTO.builder()
                .productId(product.getId())
                .productSku(product.getSku())
                .productName(product.getName())
                .totalQuantity(totalQuantity)
                .entries(entries)
                .build();
    }

    private List<ProductInventoryEntryDTO> fetchAllInventoryEntries(UUID productId) {
        List<ProductInventoryEntryDTO> allEntries = new ArrayList<>();

        CompletableFuture<List<ProductInventoryEntryDTO>> boxBinFuture = CompletableFuture.supplyAsync(
                () -> mapBoxBinInventory(boxBinInventoryRepository.findByItem_Id(productId)), executor);

        CompletableFuture<List<ProductInventoryEntryDTO>> rackFuture = CompletableFuture.supplyAsync(
                () -> mapRackInventory(rackInventoryRepository.findByItem_Id(productId)), executor);

        CompletableFuture<List<ProductInventoryEntryDTO>> cabinetFuture = CompletableFuture.supplyAsync(
                () -> mapCabinetInventory(cabinetInventoryRepository.findByItem_Id(productId)), executor);

        CompletableFuture<List<ProductInventoryEntryDTO>> singleClawFuture = CompletableFuture.supplyAsync(
                () -> mapSingleClawMachineInventory(singleClawMachineInventoryRepository.findByItem_Id(productId)), executor);

        CompletableFuture<List<ProductInventoryEntryDTO>> doubleClawFuture = CompletableFuture.supplyAsync(
                () -> mapDoubleClawMachineInventory(doubleClawMachineInventoryRepository.findByItem_Id(productId)), executor);

        CompletableFuture<List<ProductInventoryEntryDTO>> fourCornerFuture = CompletableFuture.supplyAsync(
                () -> mapFourCornerMachineInventory(fourCornerMachineInventoryRepository.findByItem_Id(productId)), executor);

        CompletableFuture<List<ProductInventoryEntryDTO>> pusherFuture = CompletableFuture.supplyAsync(
                () -> mapPusherMachineInventory(pusherMachineInventoryRepository.findByItem_Id(productId)), executor);

        CompletableFuture<List<ProductInventoryEntryDTO>> windowFuture = CompletableFuture.supplyAsync(
                () -> mapWindowInventory(windowInventoryRepository.findByItem_Id(productId)), executor);

        CompletableFuture<List<ProductInventoryEntryDTO>> notAssignedFuture = CompletableFuture.supplyAsync(
                () -> mapNotAssignedInventory(notAssignedInventoryRepository.findAllByItem_Id(productId)), executor);

        CompletableFuture.allOf(
                boxBinFuture, rackFuture, cabinetFuture, singleClawFuture,
                doubleClawFuture, fourCornerFuture, pusherFuture, windowFuture,
                notAssignedFuture
        ).join();

        allEntries.addAll(boxBinFuture.join());
        allEntries.addAll(rackFuture.join());
        allEntries.addAll(cabinetFuture.join());
        allEntries.addAll(singleClawFuture.join());
        allEntries.addAll(doubleClawFuture.join());
        allEntries.addAll(fourCornerFuture.join());
        allEntries.addAll(pusherFuture.join());
        allEntries.addAll(windowFuture.join());
        allEntries.addAll(notAssignedFuture.join());

        allEntries.sort((a, b) -> a.getLocationLabel().compareToIgnoreCase(b.getLocationLabel()));

        return allEntries;
    }

    /**
     * Delete all inventory records for a product across all location types.
     * Required before deleting a product to avoid FK constraint violations.
     */
    @Transactional
    public void deleteAllInventoryForProduct(UUID productId) {
        boxBinInventoryRepository.deleteByItem_Id(productId);
        rackInventoryRepository.deleteByItem_Id(productId);
        cabinetInventoryRepository.deleteByItem_Id(productId);
        singleClawMachineInventoryRepository.deleteByItem_Id(productId);
        doubleClawMachineInventoryRepository.deleteByItem_Id(productId);
        fourCornerMachineInventoryRepository.deleteByItem_Id(productId);
        pusherMachineInventoryRepository.deleteByItem_Id(productId);
        windowInventoryRepository.deleteByItem_Id(productId);
        notAssignedInventoryRepository.deleteByItem_Id(productId);
    }

    private List<ProductInventoryEntryDTO> mapBoxBinInventory(List<BoxBinInventory> inventories) {
        return inventories.stream()
                .map(inv -> ProductInventoryEntryDTO.builder()
                        .inventoryId(inv.getId())
                        .locationType(LocationType.BOX_BIN.name())
                        .locationId(inv.getBoxBin().getId())
                        .locationCode(inv.getBoxBin().getBoxBinCode())
                        .locationLabel("Box Bin " + inv.getBoxBin().getBoxBinCode())
                        .quantity(inv.getQuantity())
                        .updatedAt(inv.getUpdatedAt())
                        .build())
                .toList();
    }

    private List<ProductInventoryEntryDTO> mapRackInventory(List<RackInventory> inventories) {
        return inventories.stream()
                .map(inv -> ProductInventoryEntryDTO.builder()
                        .inventoryId(inv.getId())
                        .locationType(LocationType.RACK.name())
                        .locationId(inv.getRack().getId())
                        .locationCode(inv.getRack().getRackCode())
                        .locationLabel("Rack " + inv.getRack().getRackCode())
                        .quantity(inv.getQuantity())
                        .updatedAt(inv.getUpdatedAt())
                        .build())
                .toList();
    }

    private List<ProductInventoryEntryDTO> mapCabinetInventory(List<CabinetInventory> inventories) {
        return inventories.stream()
                .map(inv -> ProductInventoryEntryDTO.builder()
                        .inventoryId(inv.getId())
                        .locationType(LocationType.CABINET.name())
                        .locationId(inv.getCabinet().getId())
                        .locationCode(inv.getCabinet().getCabinetCode())
                        .locationLabel("Cabinet " + inv.getCabinet().getCabinetCode())
                        .quantity(inv.getQuantity())
                        .updatedAt(inv.getUpdatedAt())
                        .build())
                .toList();
    }

    private List<ProductInventoryEntryDTO> mapSingleClawMachineInventory(List<SingleClawMachineInventory> inventories) {
        return inventories.stream()
                .map(inv -> ProductInventoryEntryDTO.builder()
                        .inventoryId(inv.getId())
                        .locationType(LocationType.SINGLE_CLAW_MACHINE.name())
                        .locationId(inv.getSingleClawMachine().getId())
                        .locationCode(inv.getSingleClawMachine().getSingleClawMachineCode())
                        .locationLabel("Single Claw " + inv.getSingleClawMachine().getSingleClawMachineCode())
                        .quantity(inv.getQuantity())
                        .updatedAt(inv.getUpdatedAt())
                        .build())
                .toList();
    }

    private List<ProductInventoryEntryDTO> mapDoubleClawMachineInventory(List<DoubleClawMachineInventory> inventories) {
        return inventories.stream()
                .map(inv -> ProductInventoryEntryDTO.builder()
                        .inventoryId(inv.getId())
                        .locationType(LocationType.DOUBLE_CLAW_MACHINE.name())
                        .locationId(inv.getDoubleClawMachine().getId())
                        .locationCode(inv.getDoubleClawMachine().getDoubleClawMachineCode())
                        .locationLabel("Double Claw " + inv.getDoubleClawMachine().getDoubleClawMachineCode())
                        .quantity(inv.getQuantity())
                        .updatedAt(inv.getUpdatedAt())
                        .build())
                .toList();
    }

    private List<ProductInventoryEntryDTO> mapFourCornerMachineInventory(List<FourCornerMachineInventory> inventories) {
        return inventories.stream()
                .map(inv -> ProductInventoryEntryDTO.builder()
                        .inventoryId(inv.getId())
                        .locationType(LocationType.FOUR_CORNER_MACHINE.name())
                        .locationId(inv.getFourCornerMachine().getId())
                        .locationCode(inv.getFourCornerMachine().getFourCornerMachineCode())
                        .locationLabel("Four Corner " + inv.getFourCornerMachine().getFourCornerMachineCode())
                        .quantity(inv.getQuantity())
                        .updatedAt(inv.getUpdatedAt())
                        .build())
                .toList();
    }

    private List<ProductInventoryEntryDTO> mapPusherMachineInventory(List<PusherMachineInventory> inventories) {
        return inventories.stream()
                .map(inv -> ProductInventoryEntryDTO.builder()
                        .inventoryId(inv.getId())
                        .locationType(LocationType.PUSHER_MACHINE.name())
                        .locationId(inv.getPusherMachine().getId())
                        .locationCode(inv.getPusherMachine().getPusherMachineCode())
                        .locationLabel("Pusher " + inv.getPusherMachine().getPusherMachineCode())
                        .quantity(inv.getQuantity())
                        .updatedAt(inv.getUpdatedAt())
                        .build())
                .toList();
    }

    private List<ProductInventoryEntryDTO> mapWindowInventory(List<WindowInventory> inventories) {
        return inventories.stream()
                .map(inv -> ProductInventoryEntryDTO.builder()
                        .inventoryId(inv.getId())
                        .locationType(LocationType.WINDOW.name())
                        .locationId(inv.getWindow().getId())
                        .locationCode(inv.getWindow().getWindowCode())
                        .locationLabel("Window " + inv.getWindow().getWindowCode())
                        .quantity(inv.getQuantity())
                        .updatedAt(inv.getUpdatedAt())
                        .build())
                .toList();
    }

    private List<ProductInventoryEntryDTO> mapNotAssignedInventory(List<NotAssignedInventory> inventories) {
        return inventories.stream()
                .map(inv -> ProductInventoryEntryDTO.builder()
                        .inventoryId(inv.getId())
                        .locationType(LocationType.NOT_ASSIGNED.name())
                        .locationId(null)
                        .locationCode("NA")
                        .locationLabel("Not Assigned")
                        .quantity(inv.getQuantity())
                        .updatedAt(inv.getUpdatedAt())
                        .build())
                .toList();
    }
}
