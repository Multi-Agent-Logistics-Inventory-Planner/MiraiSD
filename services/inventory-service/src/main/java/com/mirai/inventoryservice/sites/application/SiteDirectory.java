package com.mirai.inventoryservice.sites.application;

import com.mirai.inventoryservice.sites.domain.Site;
import com.mirai.inventoryservice.sites.infrastructure.SiteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only facade onto {@code sites}' repositories, per
 * docs/specs/spring-domain-modular-monolith.md section 7: other modules (identity's
 * AuthorizedSiteContextFactory in particular) resolve site existence/details through this,
 * never through {@link SiteRepository} directly.
 */
@Service
@Transactional(readOnly = true)
public class SiteDirectory {

    private final SiteRepository siteRepository;

    public SiteDirectory(SiteRepository siteRepository) {
        this.siteRepository = siteRepository;
    }

    public Optional<SiteSummary> findById(UUID siteId) {
        return siteRepository.findById(siteId).map(SiteDirectory::toSummary);
    }

    public Optional<SiteSummary> findByCode(String code) {
        return siteRepository.findByCode(code).map(SiteDirectory::toSummary);
    }

    public boolean exists(UUID siteId) {
        return siteRepository.existsById(siteId);
    }

    public List<SiteSummary> allSites() {
        return siteRepository.findAll().stream().map(SiteDirectory::toSummary).toList();
    }

    private static SiteSummary toSummary(Site site) {
        return new SiteSummary(site.getId(), site.getName(), site.getCode());
    }
}
