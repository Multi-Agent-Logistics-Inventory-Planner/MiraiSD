package com.mirai.inventoryservice.dtos.easypost;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EasyPostTrackerResult {
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

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EasyPostTrackingDetail {
        private String status;
        private String message;
        private String datetime;

        @JsonProperty("tracking_location")
        private EasyPostTrackingLocation trackingLocation;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EasyPostTrackingLocation {
        private String city;
        private String state;
        private String country;
    }
}
