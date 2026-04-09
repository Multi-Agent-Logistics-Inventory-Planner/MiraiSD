package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.dtos.responses.ActivityFeedEventDTO;
import com.mirai.inventoryservice.services.ActivityFeedService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/activity-feed")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER', 'EMPLOYEE')")
public class ActivityFeedController {

    private final ActivityFeedService activityFeedService;

    @GetMapping
    public ResponseEntity<List<ActivityFeedEventDTO>> getActivityFeed(
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit,
            @RequestParam(required = false) List<String> types,
            @RequestParam(defaultValue = "false") boolean includeResolved
    ) {
        return ResponseEntity.ok(activityFeedService.getActivityFeed(limit, types, includeResolved));
    }
}
