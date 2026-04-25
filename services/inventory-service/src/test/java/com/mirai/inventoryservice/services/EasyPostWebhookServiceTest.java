package com.mirai.inventoryservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirai.inventoryservice.dtos.easypost.EasyPostTrackerResult;
import com.mirai.inventoryservice.dtos.easypost.EasyPostWebhookPayload;
import com.mirai.inventoryservice.models.audit.Notification;
import com.mirai.inventoryservice.models.enums.CarrierStatus;
import com.mirai.inventoryservice.models.enums.NotificationType;
import com.mirai.inventoryservice.models.enums.ShipmentStatus;
import com.mirai.inventoryservice.models.shipment.Shipment;
import com.mirai.inventoryservice.repositories.ShipmentRepository;
import com.mirai.inventoryservice.repositories.WebhookEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EasyPostWebhookServiceTest {

    @Mock
    private ShipmentRepository shipmentRepository;
    @Mock
    private WebhookEventRepository webhookEventRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private SupabaseBroadcastService broadcastService;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private EasyPostWebhookService service;

    private Shipment shipment;

    @BeforeEach
    void setUp() {
        shipment = Shipment.builder()
                .id(UUID.randomUUID())
                .shipmentNumber("SHIP-001")
                .status(ShipmentStatus.PENDING)
                .build();
    }

    @Test
    void deliveredEventSetsCarrierStatusOnlyAndLeavesInventoryStatusUnchanged() {
        EasyPostTrackerResult tracker = new EasyPostTrackerResult();
        tracker.setId("trk_1");
        tracker.setStatus("delivered");
        EasyPostWebhookPayload payload = new EasyPostWebhookPayload();
        payload.setId("evt_1");
        payload.setDescription("tracker.updated");
        payload.setResult(tracker);

        when(webhookEventRepository.existsByEventIdAndSource("evt_1", "easypost")).thenReturn(false);
        when(shipmentRepository.findByEasypostTrackerId("trk_1")).thenReturn(Optional.of(shipment));

        service.processWebhook(payload);

        ArgumentCaptor<Shipment> captor = ArgumentCaptor.forClass(Shipment.class);
        verify(shipmentRepository).save(captor.capture());
        Shipment saved = captor.getValue();

        assertEquals(ShipmentStatus.PENDING, saved.getStatus(),
                "Inventory status must not change from a carrier-side event");
        assertEquals(CarrierStatus.DELIVERED, saved.getCarrierStatus());
        assertNotNull(saved.getCarrierDeliveredAt(), "carrier_delivered_at must be populated");
    }

    @Test
    void deliveredEventCreatesPackageArrivedNotification() {
        EasyPostTrackerResult tracker = new EasyPostTrackerResult();
        tracker.setId("trk_1");
        tracker.setStatus("delivered");
        EasyPostWebhookPayload payload = new EasyPostWebhookPayload();
        payload.setId("evt_1");
        payload.setDescription("tracker.updated");
        payload.setResult(tracker);

        when(webhookEventRepository.existsByEventIdAndSource("evt_1", "easypost")).thenReturn(false);
        when(shipmentRepository.findByEasypostTrackerId("trk_1")).thenReturn(Optional.of(shipment));

        service.processWebhook(payload);

        ArgumentCaptor<Notification> notif = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService).createNotification(notif.capture());
        assertEquals(NotificationType.PACKAGE_ARRIVED, notif.getValue().getType());
        assertTrue(notif.getValue().getMessage().contains("Please verify and receive items"));
    }

    @Test
    void inTransitEventSetsCarrierStatusInTransit() {
        EasyPostTrackerResult tracker = new EasyPostTrackerResult();
        tracker.setId("trk_1");
        tracker.setStatus("in_transit");
        EasyPostWebhookPayload payload = new EasyPostWebhookPayload();
        payload.setId("evt_2");
        payload.setDescription("tracker.updated");
        payload.setResult(tracker);

        when(webhookEventRepository.existsByEventIdAndSource("evt_2", "easypost")).thenReturn(false);
        when(shipmentRepository.findByEasypostTrackerId("trk_1")).thenReturn(Optional.of(shipment));

        service.processWebhook(payload);

        ArgumentCaptor<Shipment> captor = ArgumentCaptor.forClass(Shipment.class);
        verify(shipmentRepository).save(captor.capture());
        assertEquals(CarrierStatus.IN_TRANSIT, captor.getValue().getCarrierStatus());
        assertNull(captor.getValue().getCarrierDeliveredAt(),
                "in_transit must not set carrier_delivered_at");
        // No PACKAGE_ARRIVED notification for in_transit
        verify(notificationService, never()).createNotification(any());
    }

    @Test
    void receivedShipmentNoLongerProtectedByGuard_carrierStatusStillUpdates() {
        // Old behavior: shipment already DELIVERED - guard prevented webhook updates.
        // New behavior: status (inventory) and carrier_status are independent, so the guard is gone.
        shipment.setStatus(ShipmentStatus.RECEIVED);
        shipment.setCarrierStatus(CarrierStatus.IN_TRANSIT);
        EasyPostTrackerResult tracker = new EasyPostTrackerResult();
        tracker.setId("trk_1");
        tracker.setStatus("delivered");
        EasyPostWebhookPayload payload = new EasyPostWebhookPayload();
        payload.setId("evt_3");
        payload.setDescription("tracker.updated");
        payload.setResult(tracker);

        when(webhookEventRepository.existsByEventIdAndSource("evt_3", "easypost")).thenReturn(false);
        when(shipmentRepository.findByEasypostTrackerId("trk_1")).thenReturn(Optional.of(shipment));

        service.processWebhook(payload);

        ArgumentCaptor<Shipment> captor = ArgumentCaptor.forClass(Shipment.class);
        verify(shipmentRepository).save(captor.capture());
        assertEquals(ShipmentStatus.RECEIVED, captor.getValue().getStatus(),
                "Inventory status must remain RECEIVED");
        assertEquals(CarrierStatus.DELIVERED, captor.getValue().getCarrierStatus());
    }
}
