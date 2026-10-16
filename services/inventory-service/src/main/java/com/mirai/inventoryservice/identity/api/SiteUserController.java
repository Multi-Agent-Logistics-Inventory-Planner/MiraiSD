package com.mirai.inventoryservice.identity.api;

import com.mirai.inventoryservice.identity.application.UserService;
import com.mirai.inventoryservice.shared.web.AuthorizedSiteContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Site-scoped user reads. {@code siteId} is declared on each handler so the generated client
 * exposes it, but the trusted value always comes from {@link AuthorizedSiteContextHolder}.
 */
@RestController
@RequestMapping("/api/v1/sites/{siteId}/users")
@PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER', 'EMPLOYEE')")
public class SiteUserController {

    private final UserService userService;
    private final UserMapper userMapper;

    public SiteUserController(UserService userService, UserMapper userMapper) {
        this.userService = userService;
        this.userMapper = userMapper;
    }

    private UUID contextSiteId() {
        return AuthorizedSiteContextHolder.require().siteId();
    }

    @GetMapping
    public ResponseEntity<List<UserResponseDTO>> getSiteUsers(@PathVariable UUID siteId) {
        return ResponseEntity.ok(userMapper.toResponseDTOList(userService.findActiveSiteMembers(contextSiteId())));
    }

    @GetMapping("/last-audits")
    public ResponseEntity<Map<UUID, OffsetDateTime>> getSiteLastAudits(@PathVariable UUID siteId) {
        return ResponseEntity.ok(userService.getSiteLastAuditDates(contextSiteId()));
    }
}
