package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.catalog.application.CatalogEntityAccess;
import com.mirai.inventoryservice.inventory.application.InventoryOperations;
import com.mirai.inventoryservice.inventory.application.InventoryQueries;
import com.mirai.inventoryservice.catalog.application.CatalogQueries;
import com.mirai.inventoryservice.catalog.application.ProductRef;
import com.mirai.inventoryservice.catalog.application.ProductService;
import com.mirai.inventoryservice.catalog.application.ProductStockStateWriter;
import com.mirai.inventoryservice.catalog.domain.KujiType;
import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.dtos.requests.kuji.AddKujiTierRequestDTO;
import com.mirai.inventoryservice.dtos.requests.kuji.CloseKujiBoxRequestDTO;
import com.mirai.inventoryservice.dtos.requests.kuji.NewKujiBoxTierDTO;
import com.mirai.inventoryservice.dtos.requests.kuji.OpenKujiBoxRequestDTO;
import com.mirai.inventoryservice.dtos.requests.kuji.PatchKujiTierRequestDTO;
import com.mirai.inventoryservice.models.audit.AuditLog;
import com.mirai.inventoryservice.inventory.domain.StockMovement;
import com.mirai.inventoryservice.models.enums.KujiBoxStatus;
import com.mirai.inventoryservice.inventory.domain.LocationInventory;
import com.mirai.inventoryservice.models.kuji.KujiBox;
import com.mirai.inventoryservice.models.kuji.KujiBoxTier;
import com.mirai.inventoryservice.sites.domain.Location;
import com.mirai.inventoryservice.sites.domain.Site;
import com.mirai.inventoryservice.sites.domain.StorageLocation;
import com.mirai.inventoryservice.repositories.AuditLogRepository;
import com.mirai.inventoryservice.repositories.KujiBoxRepository;
import com.mirai.inventoryservice.repositories.KujiBoxTierRepository;
import com.mirai.inventoryservice.sites.infrastructure.LocationRepository;
import com.mirai.inventoryservice.repositories.MachineDisplayRepository;
import com.mirai.inventoryservice.identity.infrastructure.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AC-5b for T-4b (docs: .specs/phase-5b-catalog-facade/spec.md): after migrating
 * {@code KujiBoxService} off direct {@code ProductRepository} access, {@code CatalogEntityAccess
 * .getReference} must be called for exactly the 4 genuine origins T-0's table records for this
 * class (log.md origins #8-#11: openBox's own product, openBox's existing-linked-product tier
 * branch, addTier's existing-linked-product branch, patchTier's linked-product-change branch) —
 * and NEVER for the auto-create / isActive-flip branches (openBox line 263, closeBox's
 * soft-delete, reopenBox's reactivate, addTier's auto-create), which route through
 * {@link ProductStockStateWriter} instead. Each test below asserts both halves: the presence of
 * {@code getReference} where T-0 requires it, and its absence where T-0 says it must not appear.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KujiBoxServiceCatalogFacadeMigrationTest {

    @Mock private KujiBoxRepository kujiBoxRepository;
    @Mock private KujiBoxTierRepository kujiBoxTierRepository;
    @Mock private CatalogQueries catalogQueries;
    @Mock private CatalogEntityAccess catalogEntityAccess;
    @Mock private ProductStockStateWriter productStockStateWriter;
    @Mock private LocationRepository locationRepository;
    @Mock private InventoryOperations inventoryOperations;
    @Mock private InventoryQueries inventoryQueries;
    @Mock private MachineDisplayRepository machineDisplayRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;
    @Mock private SupabaseBroadcastService broadcastService;
    @Mock private EntityManager entityManager;
    @Mock private ProductService productService;

    private KujiBoxService service;

    private UUID parentProductId;
    private UUID sourceLocationId;
    private UUID boxLocationId;
    private Location boxLocation;
    private Location sourceLocation;
    private Site site;

    @BeforeEach
    void setUp() {
        service = new KujiBoxService(
                kujiBoxRepository,
                kujiBoxTierRepository,
                catalogQueries,
                catalogEntityAccess,
                productStockStateWriter,
                locationRepository,
                inventoryOperations,
                inventoryQueries,
                machineDisplayRepository,
                auditLogRepository,
                userRepository,
                notificationService,
                broadcastService,
                entityManager,
                productService);

        parentProductId = UUID.randomUUID();
        sourceLocationId = UUID.randomUUID();
        boxLocationId = UUID.randomUUID();

        site = Site.builder().id(UUID.randomUUID()).code("MAIN").name("Main").build();
        StorageLocation sourceStorage = StorageLocation.builder()
                .id(UUID.randomUUID()).site(site).code("RACKS").name("Racks").build();
        StorageLocation boxStorage = StorageLocation.builder()
                .id(UUID.randomUUID()).site(site).code("BOX_BINS").name("Box Bins").build();
        sourceLocation = Location.builder()
                .id(sourceLocationId).storageLocation(sourceStorage).locationCode("S1").build();
        boxLocation = Location.builder()
                .id(boxLocationId).storageLocation(boxStorage).locationCode("B1").build();

        when(locationRepository.findById(boxLocationId)).thenReturn(Optional.of(boxLocation));
        when(locationRepository.findById(sourceLocationId)).thenReturn(Optional.of(sourceLocation));
        when(kujiBoxRepository.save(any(KujiBox.class))).thenAnswer(inv -> {
            KujiBox b = inv.getArgument(0);
            if (b.getId() == null) {
                b.setId(UUID.randomUUID());
            }
            return b;
        });
        when(kujiBoxTierRepository.save(any(KujiBoxTier.class))).thenAnswer(inv -> inv.getArgument(0));
        when(inventoryOperations.recordMovement(any(StockMovement.class))).thenAnswer(inv -> inv.getArgument(0));
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Product parentEntity() {
        Product p = new Product();
        p.setId(parentProductId);
        p.setName("Kuji Parent");
        p.setKujiType(KujiType.CUSTOM);
        return p;
    }

    private OpenKujiBoxRequestDTO.OpenKujiBoxRequestDTOBuilder openRequestBuilder() {
        return OpenKujiBoxRequestDTO.builder()
                .productId(parentProductId)
                .locationId(boxLocationId)
                .actorId(UUID.randomUUID());
    }

    @Test
    void openBox_existingLinkedProductTier_getsCatalogEntityAccessForParentAndLinkedProductOnly() {
        when(catalogQueries.findById(parentProductId))
                .thenReturn(Optional.of(ProductRef.from(parentEntity())));
        when(catalogEntityAccess.getReference(parentProductId)).thenReturn(parentEntity());

        UUID linkedId = UUID.randomUUID();
        Product linkedEntity = new Product();
        linkedEntity.setId(linkedId);
        linkedEntity.setName("Prize Widget");
        when(catalogQueries.findById(linkedId)).thenReturn(Optional.of(ProductRef.from(linkedEntity)));
        when(catalogEntityAccess.getReference(linkedId)).thenReturn(linkedEntity);

        when(inventoryOperations.findInventory(sourceLocationId, linkedId))
                .thenReturn(Optional.of(LocationInventory.builder()
                        .id(UUID.randomUUID()).location(sourceLocation).site(site)
                        .product(linkedEntity).quantity(10).build()));

        OpenKujiBoxRequestDTO request = openRequestBuilder()
                .tiers(List.of(NewKujiBoxTierDTO.builder()
                        .label("Tier A")
                        .linkedProductId(linkedId)
                        .sourceLocationId(sourceLocationId)
                        .activeCount(2)
                        .inactiveCount(0)
                        .build()))
                .build();

        service.openBox(request);

        verify(catalogEntityAccess, times(1)).getReference(parentProductId);
        verify(catalogEntityAccess, times(1)).getReference(linkedId);
        verify(catalogEntityAccess, times(2)).getReference(any());
        verify(productStockStateWriter, never()).setActive(any(), anyBoolean());
        verify(productStockStateWriter, never()).applyStockState(any(), anyInt(), anyBoolean());
    }

    @Test
    void openBox_autoCreateTier_neverCallsCatalogEntityAccessForCreatedChildOnlyForParent() {
        when(catalogQueries.findById(parentProductId))
                .thenReturn(Optional.of(ProductRef.from(parentEntity())));
        when(catalogEntityAccess.getReference(parentProductId)).thenReturn(parentEntity());

        UUID createdId = UUID.randomUUID();
        Product created = new Product();
        created.setId(createdId);
        created.setName("Auto Prize");
        when(productService.createProduct(
                any(), any(), eq(parentProductId), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(created);

        OpenKujiBoxRequestDTO request = openRequestBuilder()
                .tiers(List.of(NewKujiBoxTierDTO.builder()
                        .label("Tier A")
                        .autoCreate(true)
                        .productName("Auto Prize")
                        .activeCount(0)
                        .inactiveCount(0)
                        .build()))
                .build();

        service.openBox(request);

        verify(catalogEntityAccess, times(1)).getReference(parentProductId);
        verify(catalogEntityAccess, never()).getReference(createdId);
        verify(productStockStateWriter, times(1)).setActive(createdId, true);
    }

    @Test
    void closeBox_autoCreatedTierWithLeftover_flipsIsActiveFalseThroughWriterNotCatalogEntityAccess() {
        UUID childId = UUID.randomUUID();
        Product child = new Product();
        child.setId(childId);
        child.setName("Auto Prize");

        KujiBoxTier tier = KujiBoxTier.builder()
                .id(UUID.randomUUID())
                .autoCreatedProduct(true)
                .linkedProduct(child)
                .activeCount(1)
                .inactiveCount(0)
                .build();
        KujiBox box = KujiBox.builder()
                .id(UUID.randomUUID())
                .product(parentEntity())
                .location(boxLocation)
                .status(KujiBoxStatus.OPEN)
                .tiers(new ArrayList<>(List.of(tier)))
                .build();
        when(kujiBoxRepository.findByIdWithTiers(box.getId())).thenReturn(Optional.of(box));

        service.closeBox(box.getId(), CloseKujiBoxRequestDTO.builder()
                .actorId(UUID.randomUUID())
                .build());

        verify(productStockStateWriter, times(1)).setActive(childId, false);
        verify(catalogEntityAccess, never()).getReference(any());
    }

    @Test
    void reopenBox_reactivatesSoftDeletedAutoCreatedChildThroughWriterNotCatalogEntityAccess() {
        UUID childId = UUID.randomUUID();
        Product child = new Product();
        child.setId(childId);
        child.setName("Auto Prize");
        child.setIsActive(false);

        KujiBoxTier tier = KujiBoxTier.builder()
                .id(UUID.randomUUID())
                .autoCreatedProduct(true)
                .linkedProduct(child)
                .activeCount(0)
                .inactiveCount(0)
                .build();
        Product parent = parentEntity();
        KujiBox box = KujiBox.builder()
                .id(UUID.randomUUID())
                .product(parent)
                .location(boxLocation)
                .status(KujiBoxStatus.CLOSED)
                .closedAt(null)
                .tiers(new ArrayList<>(List.of(tier)))
                .build();
        when(kujiBoxRepository.findByIdWithTiers(box.getId())).thenReturn(Optional.of(box));
        when(kujiBoxRepository.findByProductIdAndStatus(parent.getId(), KujiBoxStatus.OPEN))
                .thenReturn(Optional.empty());

        service.reopenBox(box.getId(), UUID.randomUUID());

        verify(productStockStateWriter, times(1)).setActive(childId, true);
        verify(catalogEntityAccess, never()).getReference(any());
    }

    @Test
    void addTier_existingLinkedProduct_usesCatalogEntityAccessGetReference() {
        UUID boxId = UUID.randomUUID();
        KujiBox box = KujiBox.builder()
                .id(boxId).product(parentEntity()).location(boxLocation)
                .status(KujiBoxStatus.OPEN).tiers(new ArrayList<>())
                .build();
        when(kujiBoxRepository.findByIdWithTiers(boxId)).thenReturn(Optional.of(box));

        UUID linkedId = UUID.randomUUID();
        Product linkedEntity = new Product();
        linkedEntity.setId(linkedId);
        linkedEntity.setName("Existing Prize");
        when(catalogQueries.findById(linkedId)).thenReturn(Optional.of(ProductRef.from(linkedEntity)));
        when(catalogEntityAccess.getReference(linkedId)).thenReturn(linkedEntity);

        when(inventoryOperations.findInventory(sourceLocationId, linkedId))
                .thenReturn(Optional.of(LocationInventory.builder()
                        .id(UUID.randomUUID()).location(sourceLocation).site(site)
                        .product(linkedEntity).quantity(10).build()));

        AddKujiTierRequestDTO request = AddKujiTierRequestDTO.builder()
                .actorId(UUID.randomUUID())
                .label("Tier A")
                .linkedProductId(linkedId)
                .sourceLocationId(sourceLocationId)
                .activeCount(2)
                .inactiveCount(0)
                .build();

        service.addTier(boxId, request);

        verify(catalogEntityAccess, times(1)).getReference(linkedId);
        verify(productStockStateWriter, never()).setActive(any(), anyBoolean());
    }

    @Test
    void addTier_autoCreate_neverCallsCatalogEntityAccessSetsActiveThroughWriter() {
        UUID boxId = UUID.randomUUID();
        Product parent = parentEntity();
        KujiBox box = KujiBox.builder()
                .id(boxId).product(parent).location(boxLocation)
                .status(KujiBoxStatus.OPEN).tiers(new ArrayList<>())
                .build();
        when(kujiBoxRepository.findByIdWithTiers(boxId)).thenReturn(Optional.of(box));

        UUID createdId = UUID.randomUUID();
        Product created = new Product();
        created.setId(createdId);
        created.setName("Auto Prize");
        when(productService.createProduct(
                any(), any(), eq(parent.getId()), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(created);

        AddKujiTierRequestDTO request = AddKujiTierRequestDTO.builder()
                .actorId(UUID.randomUUID())
                .label("Tier A")
                .autoCreate(true)
                .productName("Auto Prize")
                .activeCount(0)
                .inactiveCount(0)
                .build();

        service.addTier(boxId, request);

        verify(catalogEntityAccess, never()).getReference(any());
        verify(productStockStateWriter, times(1)).setActive(createdId, true);
    }

    @Test
    void patchTier_changeLinkedProduct_usesCatalogEntityAccessGetReference() {
        UUID boxId = UUID.randomUUID();
        UUID tierId = UUID.randomUUID();
        KujiBoxTier tier = KujiBoxTier.builder()
                .id(tierId)
                .label("Tier A")
                .activeCount(0)
                .inactiveCount(0)
                .build();
        KujiBox box = KujiBox.builder()
                .id(boxId).product(parentEntity()).location(boxLocation)
                .status(KujiBoxStatus.OPEN).tiers(new ArrayList<>(List.of(tier)))
                .build();
        tier.setBox(box);
        when(kujiBoxRepository.findByIdWithTiers(boxId)).thenReturn(Optional.of(box));
        when(kujiBoxTierRepository.findByIdForUpdate(tierId)).thenReturn(Optional.of(tier));

        UUID newLinkedId = UUID.randomUUID();
        Product newLinkedEntity = new Product();
        newLinkedEntity.setId(newLinkedId);
        newLinkedEntity.setName("New Linked Prize");
        when(catalogQueries.findById(newLinkedId)).thenReturn(Optional.of(ProductRef.from(newLinkedEntity)));
        when(catalogEntityAccess.getReference(newLinkedId)).thenReturn(newLinkedEntity);

        PatchKujiTierRequestDTO request = PatchKujiTierRequestDTO.builder()
                .actorId(UUID.randomUUID())
                .linkedProductId(newLinkedId)
                .build();

        service.patchTier(boxId, tierId, request);

        verify(catalogEntityAccess, times(1)).getReference(newLinkedId);
        assertEquals(newLinkedEntity, tier.getLinkedProduct());
    }
}
