package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.dtos.requests.BatchDisplaySwapRequestDTO;
import com.mirai.inventoryservice.dtos.requests.RenewDisplayRequestDTO;
import com.mirai.inventoryservice.dtos.requests.SetMachineDisplayRequestDTO;
import com.mirai.inventoryservice.models.MachineDisplay;
import com.mirai.inventoryservice.models.Product;
import com.mirai.inventoryservice.models.audit.AuditLog;
import com.mirai.inventoryservice.models.audit.Notification;
import com.mirai.inventoryservice.models.audit.User;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.models.enums.NotificationType;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * Verifies that every display-changing operation enqueues exactly one Notification
 * with a per-machine before/after payload, and that a notifier failure never rolls back
 * the underlying display change.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MachineDisplayServiceNotificationTest {

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
    private UUID targetMachineId;

    @BeforeEach
    void setUp() {
        service = new MachineDisplayService(
                machineDisplayRepository,
                productRepository,
                userRepository,
                stockMovementRepository,
                locationRepository,
                entityManager,
                auditLogService,
                notificationService);

        actorId = UUID.randomUUID();
        machineId = UUID.randomUUID();
        targetMachineId = UUID.randomUUID();

        // Audit log creation always succeeds with a stub
        when(auditLogService.createAuditLog(any(), any(), any(), any(), any(), any(), anyInt(), anyInt(), any(), any()))
                .thenReturn(AuditLog.builder().id(UUID.randomUUID()).build());

        // Location lookups return a placeholder
        Location loc = new Location();
        loc.setId(machineId);
        loc.setLocationCode("R2");
        Location targetLoc = new Location();
        targetLoc.setId(targetMachineId);
        targetLoc.setLocationCode("S5");
        when(locationRepository.findById(machineId)).thenReturn(Optional.of(loc));
        when(locationRepository.findById(targetMachineId)).thenReturn(Optional.of(targetLoc));

        // Actor lookup
        User user = new User();
        user.setId(actorId);
        when(userRepository.findById(actorId)).thenReturn(Optional.of(user));
    }

    private Product product(String name) {
        Product p = new Product();
        p.setId(UUID.randomUUID());
        p.setName(name);
        return p;
    }

    private MachineDisplay display(UUID machine, Product p) {
        return MachineDisplay.builder()
                .id(UUID.randomUUID())
                .machineId(machine)
                .locationType(LocationType.SINGLE_CLAW_MACHINE)
                .product(p)
                .startedAt(OffsetDateTime.now())
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> machinesIn(Notification n) {
        return (List<Map<String, Object>>) n.getMetadata().get("machines");
    }

    @Test
    void setDisplay_emitsDisplaySetNotification() {
        Product newProduct = product("Sonny V3");
        Product existing = product("Sonny V1");

        when(machineDisplayRepository.findActiveByLocationTypeAndMachineId(LocationType.SINGLE_CLAW_MACHINE, machineId))
                .thenReturn(List.of(display(machineId, existing)));
        when(productRepository.findById(newProduct.getId())).thenReturn(Optional.of(newProduct));
        when(machineDisplayRepository.save(any(MachineDisplay.class))).thenAnswer(inv -> inv.getArgument(0));

        SetMachineDisplayRequestDTO req = SetMachineDisplayRequestDTO.builder()
                .locationType(LocationType.SINGLE_CLAW_MACHINE)
                .machineId(machineId)
                .productId(newProduct.getId())
                .actorId(actorId)
                .build();
        service.setDisplay(req);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService).createNotification(captor.capture());
        Notification notif = captor.getValue();
        assertEquals(NotificationType.DISPLAY_SET, notif.getType());
        assertEquals(List.of("slack"), notif.getVia());

        List<Map<String, Object>> machines = machinesIn(notif);
        assertEquals(1, machines.size());
        assertEquals("R2", machines.get(0).get("code"));
        assertEquals(List.of("Sonny V1"), machines.get(0).get("previously"));
        assertEquals(List.of("Sonny V1", "Sonny V3"), machines.get(0).get("currently"));
    }

    @Test
    void clearDisplayById_emitsDisplayRemovedWithCorrectBeforeAfter() {
        Product removed = product("Sonny V1");
        Product staying = product("Sonny V2");
        MachineDisplay removedDisplay = display(machineId, removed);
        MachineDisplay stayingDisplay = display(machineId, staying);

        when(machineDisplayRepository.findByIdWithProduct(removedDisplay.getId()))
                .thenReturn(Optional.of(removedDisplay));
        when(machineDisplayRepository.findActiveByLocationTypeAndMachineId(LocationType.SINGLE_CLAW_MACHINE, machineId))
                .thenReturn(List.of(removedDisplay, stayingDisplay));
        when(machineDisplayRepository.save(any(MachineDisplay.class))).thenAnswer(inv -> inv.getArgument(0));

        service.clearDisplayById(removedDisplay.getId(), actorId);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService).createNotification(captor.capture());
        Notification notif = captor.getValue();
        assertEquals(NotificationType.DISPLAY_REMOVED, notif.getType());

        List<Map<String, Object>> machines = machinesIn(notif);
        assertEquals(1, machines.size());
        assertEquals(List.of("Sonny V1", "Sonny V2"), machines.get(0).get("previously"));
        assertEquals(List.of("Sonny V2"), machines.get(0).get("currently"));
    }

    @Test
    void batchSwapDisplay_machineToMachine_emitsTwoMachineSnapshots() {
        Product p1 = product("Sonny V1");
        Product p2 = product("Sonny V2");

        // Source machine starts with p1
        MachineDisplay sourceDisplayP1 = display(machineId, p1);
        // Target machine starts with p2
        MachineDisplay targetDisplayP2 = display(targetMachineId, p2);

        when(machineDisplayRepository.findActiveByLocationTypeAndMachineId(LocationType.SINGLE_CLAW_MACHINE, machineId))
                .thenReturn(List.of(sourceDisplayP1));
        when(machineDisplayRepository.findActiveByLocationTypeAndMachineId(LocationType.SINGLE_CLAW_MACHINE, targetMachineId))
                .thenReturn(List.of(targetDisplayP2));
        when(machineDisplayRepository.findByIdWithProduct(sourceDisplayP1.getId()))
                .thenReturn(Optional.of(sourceDisplayP1));
        when(machineDisplayRepository.findByIdWithProduct(targetDisplayP2.getId()))
                .thenReturn(Optional.of(targetDisplayP2));
        when(machineDisplayRepository.save(any(MachineDisplay.class))).thenAnswer(inv -> inv.getArgument(0));

        BatchDisplaySwapRequestDTO req = BatchDisplaySwapRequestDTO.builder()
                .locationType(LocationType.SINGLE_CLAW_MACHINE)
                .machineId(machineId)
                .targetLocationType(LocationType.SINGLE_CLAW_MACHINE)
                .targetMachineId(targetMachineId)
                .displayIdsToTarget(List.of(sourceDisplayP1.getId()))    // send p1 to S5
                .displayIdsFromTarget(List.of(targetDisplayP2.getId()))  // bring p2 to R2
                .actorId(actorId)
                .build();
        service.batchSwapDisplay(req);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService).createNotification(captor.capture());
        Notification notif = captor.getValue();
        assertEquals(NotificationType.DISPLAY_SWAP, notif.getType());

        List<Map<String, Object>> machines = machinesIn(notif);
        assertEquals(2, machines.size(), "Expected one snapshot per machine");

        Map<String, Object> source = machines.stream()
                .filter(m -> "R2".equals(m.get("code"))).findFirst().orElseThrow();
        Map<String, Object> target = machines.stream()
                .filter(m -> "S5".equals(m.get("code"))).findFirst().orElseThrow();

        assertEquals(List.of("Sonny V1"), source.get("previously"));
        assertEquals(List.of("Sonny V2"), source.get("currently"));
        assertEquals(List.of("Sonny V2"), target.get("previously"));
        assertEquals(List.of("Sonny V1"), target.get("currently"));
    }

    @Test
    void batchSwapDisplay_noChanges_doesNotEmitNotification() {
        // No displays to remove, no products to add, no target — displayChanges stays empty
        when(machineDisplayRepository.findActiveByLocationTypeAndMachineId(LocationType.SINGLE_CLAW_MACHINE, machineId))
                .thenReturn(List.of());

        BatchDisplaySwapRequestDTO req = BatchDisplaySwapRequestDTO.builder()
                .locationType(LocationType.SINGLE_CLAW_MACHINE)
                .machineId(machineId)
                .actorId(actorId)
                .build();
        service.batchSwapDisplay(req);

        verify(notificationService, never()).createNotification(any());
    }

    @Test
    void renewDisplays_emitsRenewedWithIdenticalBeforeAfter() {
        Product p = product("Sonny V1");
        MachineDisplay existing = display(machineId, p);

        when(machineDisplayRepository.findByIdWithProduct(existing.getId()))
                .thenReturn(Optional.of(existing));
        when(machineDisplayRepository.findActiveByLocationTypeAndMachineId(LocationType.SINGLE_CLAW_MACHINE, machineId))
                .thenReturn(List.of(existing));
        when(machineDisplayRepository.save(any(MachineDisplay.class))).thenAnswer(inv -> inv.getArgument(0));

        RenewDisplayRequestDTO req = RenewDisplayRequestDTO.builder()
                .locationType(LocationType.SINGLE_CLAW_MACHINE)
                .machineId(machineId)
                .displayIds(List.of(existing.getId()))
                .actorId(actorId)
                .build();
        service.renewDisplays(req);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService).createNotification(captor.capture());
        Notification notif = captor.getValue();
        assertEquals(NotificationType.DISPLAY_RENEWED, notif.getType());

        List<Map<String, Object>> machines = machinesIn(notif);
        assertEquals(machines.get(0).get("previously"), machines.get(0).get("currently"),
                "Renew leaves the displayed product list unchanged");
    }

    @Test
    void notificationFailure_doesNotRollBackDisplayChange() {
        // Set up a successful setDisplay path...
        Product newProduct = product("Sonny V3");
        when(machineDisplayRepository.findActiveByLocationTypeAndMachineId(LocationType.SINGLE_CLAW_MACHINE, machineId))
                .thenReturn(List.of());
        when(productRepository.findById(newProduct.getId())).thenReturn(Optional.of(newProduct));
        when(machineDisplayRepository.save(any(MachineDisplay.class))).thenAnswer(inv -> inv.getArgument(0));
        // ...but make the notification insert blow up.
        doThrow(new RuntimeException("simulated DB outage"))
                .when(notificationService).createNotification(any());

        SetMachineDisplayRequestDTO req = SetMachineDisplayRequestDTO.builder()
                .locationType(LocationType.SINGLE_CLAW_MACHINE)
                .machineId(machineId)
                .productId(newProduct.getId())
                .actorId(actorId)
                .build();

        // Should NOT throw
        assertDoesNotThrow(() -> service.setDisplay(req));

        // The display was still saved and the audit was still written.
        verify(machineDisplayRepository).save(any(MachineDisplay.class));
        verify(auditLogService).createAuditLog(
                any(), eq(StockMovementReason.DISPLAY_SET),
                any(), any(), any(), any(),
                anyInt(), anyInt(), any(), any());
    }
}
