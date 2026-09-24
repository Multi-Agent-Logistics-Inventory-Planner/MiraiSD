package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.dtos.responses.ActivityFeedEventDTO;
import com.mirai.inventoryservice.identity.application.LegacyMainSiteContextResolver;
import com.mirai.inventoryservice.services.ActivityFeedService;
import com.mirai.inventoryservice.shared.web.AuthorizedSiteContext;
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
    private final LegacyMainSiteContextResolver legacyMainSiteContextResolver;

    @GetMapping
    public ResponseEntity<List<ActivityFeedEventDTO>> getActivityFeed(
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit,
            @RequestParam(required = false) List<String> types,
            @RequestParam(defaultValue = "false") boolean includeResolved
    ) {
        AuthorizedSiteContext context = legacyMainSiteContextResolver.requireMain();
        return ResponseEntity.ok(activityFeedService.getActivityFeed(context.siteId(), limit, types, includeResolved));
    }
}
