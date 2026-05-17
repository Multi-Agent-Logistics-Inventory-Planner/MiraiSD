package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.dtos.requests.BatchClearDisplaysRequestDTO;
import com.mirai.inventoryservice.dtos.requests.BatchDisplaySwapRequestDTO;
import com.mirai.inventoryservice.models.MachineDisplay;
import com.mirai.inventoryservice.models.Product;
import com.mirai.inventoryservice.models.audit.AuditLog;
import com.mirai.inventoryservice.models.audit.User;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.models.storage.Location;
import com.mirai.inventoryservice.repositories.LocationRepository;
import com.mirai.inventoryservice.repositories.MachineDisplayRepository;
import com.mirai.inventoryservice.repositories.ProductRepository;
import com.mirai.inventoryservice.repositories.StockMovementRepository;
import com.mirai.inventoryservice.repositories.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Guard tests that fail if any batch display operation regresses to per-row
 * MachineDisplayRepository.save(...) calls inside a loop. Each batch endpoint must
 * use saveAll(...) per phase so that database write count stays bounded as N grows.
 *
 * If a future change adds a save(...) inside a per-item loop, these tests catch it
 * because verify(...).save(any()) is asserted to be never() called from these flows.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MachineDisplayServiceBatchQueryGuardTest {

    @Mock private MachineDisplayRepository machineDisplayRepository;
    @Mock private ProductRepository productRepository;
    @Mock private UserRepository userRepository;
    @Mock private StockMovementRepository stockMovementRepository;
    @Mock private LocationRepository locationRepository;
    @Mock private EntityManager entityManager;
    @Mock private AuditLogService auditLogService;
    @Mock private NotificationService notificationService;

    private MachineDisplayService service;
    private UUID actorId;
    private UUID machineId;

    @BeforeEach
    void setUp() {
        service = new MachineDisplayService(
                machineDisplayRepository, productRepository, userRepository,
                stockMovementRepository, locationRepository, entityManager,
                auditLogService, notificationService);

        actorId = UUID.randomUUID();
        machineId = UUID.randomUUID();

        when(auditLogService.createAuditLog(any(), any(), any(), any(), any(), any(), anyInt(), anyInt(), any(), any()))
                .thenReturn(AuditLog.builder().id(UUID.randomUUID()).build());

        Location loc = new Location();
        loc.setId(machineId);
        loc.setLocationCode("R2");
        when(locationRepository.findById(machineId)).thenReturn(Optional.of(loc));

        User user = new User();
        user.setId(actorId);
        when(userRepository.findById(actorId)).thenReturn(Optional.of(user));

        when(machineDisplayRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private Product product(String name) {
        Product p = new Product();
        p.setId(UUID.randomUUID());
        p.setName(name);
        return p;
    }

    private MachineDisplay display(Product p) {
        return MachineDisplay.builder()
                .id(UUID.randomUUID())
                .machineId(machineId)
                .locationType(LocationType.SINGLE_CLAW_MACHINE)
                .product(p)
                .startedAt(OffsetDateTime.now())
                .build();
    }

    @Test
    void batchClearDisplays_neverCallsSavePerRow_andCallsSaveAllOnce() {
        int n = 10;
        List<MachineDisplay> displays = IntStream.range(0, n)
                .mapToObj(i -> display(product("P" + i)))
                .toList();
        List<UUID> ids = displays.stream().map(MachineDisplay::getId).toList();

        when(machineDisplayRepository.findAllByIdInWithProduct(any())).thenReturn(new ArrayList<>(displays));
        when(machineDisplayRepository.findActiveByLocationTypeAndMachineId(LocationType.SINGLE_CLAW_MACHINE, machineId))
                .thenReturn(new ArrayList<>(displays))
                .thenReturn(List.of());

        BatchClearDisplaysRequestDTO req = BatchClearDisplaysRequestDTO.builder()
                .displayIds(new ArrayList<>(ids))
                .actorId(actorId)
                .build();

        service.batchClearDisplays(req);

        verify(machineDisplayRepository, never()).save(any(MachineDisplay.class));
        verify(machineDisplayRepository, times(1)).saveAll(any());
        verify(stockMovementRepository, times(1)).saveAll(any());
    }

    @Test
    void batchSwapDisplay_removeOnly_neverCallsSavePerRow() {
        int n = 10;
        List<MachineDisplay> displays = IntStream.range(0, n)
                .mapToObj(i -> display(product("P" + i)))
                .toList();
        List<UUID> ids = displays.stream().map(MachineDisplay::getId).toList();

        when(machineDisplayRepository.findActiveByLocationTypeAndMachineId(LocationType.SINGLE_CLAW_MACHINE, machineId))
                .thenReturn(new ArrayList<>(displays));
        when(machineDisplayRepository.findAllByIdInWithProduct(any())).thenReturn(new ArrayList<>(displays));

        BatchDisplaySwapRequestDTO req = BatchDisplaySwapRequestDTO.builder()
                .locationType(LocationType.SINGLE_CLAW_MACHINE)
                .machineId(machineId)
                .displayIdsToRemove(new ArrayList<>(ids))
                .actorId(actorId)
                .build();

        service.batchSwapDisplay(req);

        verify(machineDisplayRepository, never()).save(any(MachineDisplay.class));
        verify(machineDisplayRepository, times(1)).saveAll(any());
        verify(stockMovementRepository, times(1)).saveAll(any());
    }

    @Test
    void batchSwapDisplay_addOnly_neverCallsSavePerRow() {
        int n = 10;
        List<Product> products = IntStream.range(0, n)
                .mapToObj(i -> product("Add" + i))
                .toList();
        List<UUID> productIds = products.stream().map(Product::getId).toList();

        when(machineDisplayRepository.findActiveByLocationTypeAndMachineId(LocationType.SINGLE_CLAW_MACHINE, machineId))
                .thenReturn(List.of());
        when(productRepository.findAllById(any())).thenReturn(products);

        BatchDisplaySwapRequestDTO req = BatchDisplaySwapRequestDTO.builder()
                .locationType(LocationType.SINGLE_CLAW_MACHINE)
                .machineId(machineId)
                .productIdsToAdd(new ArrayList<>(productIds))
                .actorId(actorId)
                .build();

        service.batchSwapDisplay(req);

        verify(machineDisplayRepository, never()).save(any(MachineDisplay.class));
        verify(machineDisplayRepository, times(1)).saveAll(any());
        verify(stockMovementRepository, times(1)).saveAll(any());
    }

    @Test
    void batchSwapDisplay_removeFindsBatchedNotPerRow() {
        int n = 10;
        List<MachineDisplay> displays = IntStream.range(0, n)
                .mapToObj(i -> display(product("P" + i)))
                .toList();
        List<UUID> ids = displays.stream().map(MachineDisplay::getId).toList();

        when(machineDisplayRepository.findActiveByLocationTypeAndMachineId(LocationType.SINGLE_CLAW_MACHINE, machineId))
                .thenReturn(new ArrayList<>(displays));
        when(machineDisplayRepository.findAllByIdInWithProduct(any())).thenReturn(new ArrayList<>(displays));

        BatchDisplaySwapRequestDTO req = BatchDisplaySwapRequestDTO.builder()
                .locationType(LocationType.SINGLE_CLAW_MACHINE)
                .machineId(machineId)
                .displayIdsToRemove(new ArrayList<>(ids))
                .actorId(actorId)
                .build();

        service.batchSwapDisplay(req);

        // Single batch fetch instead of N individual findByIdWithProduct calls.
        verify(machineDisplayRepository, times(1)).findAllByIdInWithProduct(any());
        verify(machineDisplayRepository, never()).findByIdWithProduct(any());
    }
}
