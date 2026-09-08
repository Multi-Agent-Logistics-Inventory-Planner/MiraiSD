package com.mirai.inventoryservice.exceptions;

import com.mirai.inventoryservice.catalog.domain.SiteProductNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only controller used solely by {@link SiteProductNotFoundExceptionMappingTest} to exercise
 * {@link GlobalExceptionHandler}'s real {@code @ExceptionHandler} dispatch for
 * {@link SiteProductNotFoundException} - no production controller exposes this exception yet
 * (planned for phase 5d). Delete both files once a real controller endpoint covers the mapping.
 */
@RestController
public class SiteProductNotFoundThrowingTestController {

    @GetMapping("/test/site-product-not-found")
    public void trigger() {
        throw new SiteProductNotFoundException("No site_products row for this pair");
    }
}
