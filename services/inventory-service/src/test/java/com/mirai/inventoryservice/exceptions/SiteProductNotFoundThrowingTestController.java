package com.mirai.inventoryservice.exceptions;

import com.mirai.inventoryservice.catalog.domain.SiteProductNotFoundException;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only controller used solely by {@link SiteProductNotFoundExceptionMappingTest} to exercise
 * {@link GlobalExceptionHandler}'s real {@code @ExceptionHandler} dispatch for
 * {@link SiteProductNotFoundException} - no production controller exposes this exception yet
 * (planned for phase 5d). Delete both files once a real controller endpoint covers the mapping.
 * <p>
 * {@code @RestController} makes this a normal classpath-scan candidate, so without a guard it
 * gets picked up by every full-context {@code @SpringBootTest} on the test classpath - including
 * {@code OpenApiContractExportTest}, which regenerates {@code packages/contracts/openapi.json}
 * from whatever the live context serves and leaked this throwaway endpoint into the checked-in
 * contract (CI's freshness check then fails on the diff). {@code @Profile(ACTIVATION_PROFILE)}
 * keeps the bean out of every context that doesn't explicitly opt in - every other
 * {@code @SpringBootTest} in this codebase runs under "test", "integration", or no profile, never
 * this one - while {@link SiteProductNotFoundExceptionMappingTest} activates it via
 * {@code @ActiveProfiles} specifically so this controller is the only bean affected.
 */
@RestController
@Profile(SiteProductNotFoundThrowingTestController.ACTIVATION_PROFILE)
public class SiteProductNotFoundThrowingTestController {

    static final String ACTIVATION_PROFILE = "site-product-not-found-test-fixture";

    @GetMapping("/test/site-product-not-found")
    public void trigger() {
        throw new SiteProductNotFoundException("No site_products row for this pair");
    }
}
