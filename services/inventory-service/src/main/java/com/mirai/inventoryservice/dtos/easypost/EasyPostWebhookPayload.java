package com.mirai.inventoryservice.dtos.easypost;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EasyPostWebhookPayload {
    private String id;
    private String mode;
    private String description;
    private EasyPostTrackerResult result;

    @JsonProperty("completed_urls")
    private List<String> completedUrls;

    @JsonProperty("pending_urls")
    private List<String> pendingUrls;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("updated_at")
    private String updatedAt;
}
