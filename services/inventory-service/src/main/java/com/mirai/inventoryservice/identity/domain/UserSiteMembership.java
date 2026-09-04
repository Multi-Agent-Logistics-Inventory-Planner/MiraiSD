package com.mirai.inventoryservice.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Grants a backend user access to a site. Carries no role: role is a global property of the
 * user ({@link User#getRole()}), not the site - see the role model decision in
 * docs/plans/enterprise-modernization.md section 7. {@code siteId} is a plain UUID rather than a
 * JPA relation to {@code sites.domain.Site}: identity and sites are separate modules and
 * docs/specs/spring-domain-modular-monolith.md rule 8 forbids new cross-module JPA entity
 * relationships. Site existence/lookup goes through {@code sites.application.SiteDirectory}.
 */
@Entity
@Table(name = "user_site_memberships",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "site_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSiteMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Version
    private Long version;
}
