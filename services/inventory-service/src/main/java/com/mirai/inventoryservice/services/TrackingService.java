package com.mirai.inventoryservice.services;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirai.inventoryservice.dtos.requests.TrackingLookupRequestDTO;
import com.mirai.inventoryservice.dtos.responses.TrackingEventDTO;
import com.mirai.inventoryservice.dtos.responses.TrackingLookupResponseDTO;
import com.mirai.inventoryservice.exceptions.TrackingException;
import com.mirai.inventoryservice.models.enums.ShipmentStatus;
import com.mirai.inventoryservice.models.shipment.Shipment;
import com.mirai.inventoryservice.repositories.ShipmentRepository;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TrackingService {

    private static final String EASYPOST_API_URL = "https://api.easypost.com/v2";

    private final ShipmentRepository shipmentRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${easypost.api.key}")
    private String apiKey;

    public TrackingService(ShipmentRepository shipmentRepository, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.shipmentRepository = shipmentRepository;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public TrackingLookupResponseDTO lookupTracking(TrackingLookupRequestDTO request) {
        log.info("=== Tracking Lookup Request ===");
        log.info("Tracking Number: {}", request.trackingNumber());
        log.info("Carrier: {}", request.carrier());
        try {
            // Create tracker via EasyPost REST API
            EasyPostTrackerResponse tracker = createTracker(request.trackingNumber(), request.carrier());

            // Try to find associated shipment in your system
            Optional<Shipment> shipmentOpt = shipmentRepository
                .findByShipmentNumber(request.trackingNumber());

            // Map to response DTO
            return mapToResponseDTO(tracker, shipmentOpt.orElse(null));

        } catch (HttpClientErrorException e) {
            log.error("=== EasyPost API Error ===");
            log.error("Status: {}", e.getStatusCode());
            log.error("Response: {}", e.getResponseBodyAsString());
            throw new TrackingException("Failed to track package: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new TrackingException("Failed to track package: " + e.getMessage(), e);
        }
    }

    public TrackingLookupResponseDTO getTracking(String trackingNumber) {
        try {
            // Create new tracker lookup
            EasyPostTrackerResponse tracker = createTracker(trackingNumber, null);

            Optional<Shipment> shipmentOpt = shipmentRepository
                .findByShipmentNumber(trackingNumber);

            return mapToResponseDTO(tracker, shipmentOpt.orElse(null));

        } catch (HttpClientErrorException e) {
            throw new TrackingException("Failed to retrieve tracking: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new TrackingException("Failed to retrieve tracking: " + e.getMessage(), e);
        }
    }

    private EasyPostTrackerResponse createTracker(String trackingCode, String carrier) {
        log.info("=== Creating EasyPost Tracker ===");
        log.info("Tracking Code: {}", trackingCode);
        log.info("Carrier: {}", carrier);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setBasicAuth(apiKey, "");  // EasyPost uses basic auth with API key as username and empty password

        // Build request as a JSON string to ensure proper formatting
        String requestBody;
        try {
            Map<String, Object> trackerRequest = new HashMap<>();
            Map<String, Object> tracker = new HashMap<>();
            tracker.put("tracking_code", trackingCode);
            if (carrier != null && !carrier.isBlank()) {
                tracker.put("carrier", carrier);
            }
            trackerRequest.put("tracker", tracker);
            requestBody = objectMapper.writeValueAsString(trackerRequest);
            log.info("Request body JSON: {}", requestBody);
        } catch (Exception e) {
            throw new TrackingException("Failed to serialize request: " + e.getMessage(), e);
        }

        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<EasyPostTrackerResponse> response = restTemplate.exchange(
            EASYPOST_API_URL + "/trackers",
            HttpMethod.POST,
            entity,
            EasyPostTrackerResponse.class
        );

        return response.getBody();
    }

    private TrackingLookupResponseDTO mapToResponseDTO(EasyPostTrackerResponse tracker, Shipment shipment) {
        String status = tracker.getStatus() != null ? tracker.getStatus() : "unknown";
        ShipmentStatus orderStatus = mapToShipmentStatus(status);

        LocalDate expectedDelivery = parseDate(tracker.getEstDeliveryDate());
        LocalDate actualDelivery = findDeliveryDate(tracker.getTrackingDetails());

        List<TrackingEventDTO> events = mapTrackingEvents(tracker.getTrackingDetails());

        return new TrackingLookupResponseDTO(
            tracker.getTrackingCode(),
            tracker.getCarrier() != null ? tracker.getCarrier() : "Unknown",
            status,
            orderStatus,
            shipment != null ? shipment.getOrderDate() : null,
            expectedDelivery,
            actualDelivery,
            tracker.getStatusDetail() != null ? tracker.getStatusDetail() : "",
            events,
            OffsetDateTime.now()
        );
    }

    private ShipmentStatus mapToShipmentStatus(String easyPostStatus) {
        if (easyPostStatus == null) {
            return ShipmentStatus.PENDING;
        }

        return switch (easyPostStatus.toLowerCase()) {
            case "in_transit", "out_for_delivery", "available_for_pickup" -> ShipmentStatus.IN_TRANSIT;
            case "delivered" -> ShipmentStatus.DELIVERED;
            case "cancelled", "return_to_sender", "failure" -> ShipmentStatus.CANCELLED;
            default -> ShipmentStatus.PENDING;
        };
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        try {
            return ZonedDateTime.parse(dateStr).toLocalDate();
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDate findDeliveryDate(List<EasyPostTrackingDetail> details) {
        if (details == null || details.isEmpty()) {
            return null;
        }

        return details.stream()
            .filter(d -> "delivered".equalsIgnoreCase(d.getStatus()))
            .findFirst()
            .map(EasyPostTrackingDetail::getDatetime)
            .map(this::parseDate)
            .orElse(null);
    }

    private List<TrackingEventDTO> mapTrackingEvents(List<EasyPostTrackingDetail> details) {
        if (details == null || details.isEmpty()) {
            return Collections.emptyList();
        }

        return details.stream()
            .map(detail -> {
                String location = formatLocation(detail);
                OffsetDateTime occurredAt = parseDateTime(detail.getDatetime());

                return new TrackingEventDTO(
                    detail.getStatus() != null ? detail.getStatus() : "",
                    detail.getMessage() != null ? detail.getMessage() : "",
                    location,
                    occurredAt
                );
            })
            .collect(Collectors.toList());
    }

    private OffsetDateTime parseDateTime(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        try {
            return ZonedDateTime.parse(dateStr).toOffsetDateTime();
        } catch (Exception e) {
            return null;
        }
    }

    private String formatLocation(EasyPostTrackingDetail detail) {
        List<String> parts = new ArrayList<>();

        if (detail.getTrackingLocation() != null) {
            EasyPostTrackingLocation loc = detail.getTrackingLocation();
            if (loc.getCity() != null) parts.add(loc.getCity());
            if (loc.getState() != null) parts.add(loc.getState());
            if (loc.getCountry() != null) parts.add(loc.getCountry());
        }

        return parts.isEmpty() ? "" : String.join(", ", parts);
    }

    // EasyPost API Response Models
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class EasyPostTrackerResponse {
        private String id;

        @JsonProperty("tracking_code")
        private String trackingCode;

        private String carrier;
        private String status;

        @JsonProperty("status_detail")
        private String statusDetail;

        @JsonProperty("est_delivery_date")
        private String estDeliveryDate;

        @JsonProperty("tracking_details")
        private List<EasyPostTrackingDetail> trackingDetails;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class EasyPostTrackingDetail {
        private String status;
        private String message;
        private String datetime;

        @JsonProperty("tracking_location")
        private EasyPostTrackingLocation trackingLocation;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class EasyPostTrackingLocation {
        private String city;
        private String state;
        private String country;
    }
}
