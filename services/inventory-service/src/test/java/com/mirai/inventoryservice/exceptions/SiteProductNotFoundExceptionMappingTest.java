package com.mirai.inventoryservice.exceptions;

import com.mirai.inventoryservice.auth.JwtService;
import com.mirai.inventoryservice.catalog.domain.SiteProductNotFoundException;
import com.mirai.inventoryservice.identity.application.AuthorizedSiteContextFactory;
import com.mirai.inventoryservice.identity.application.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves {@link SiteProductNotFoundException} actually maps to an HTTP 404 through real Spring
 * MVC dispatch (.specs/phase-5c-site-products AC-4b: a settings write against a product a site
 * has never carried "is rejected", which is only true end-to-end if the exception reaches the
 * client as a 404, not a request-level unit test calling the handler method directly). The
 * mapping is not an explicit {@code @ExceptionHandler} entry - {@link SiteProductNotFoundException}
 * extends {@link com.mirai.inventoryservice.catalog.domain.ProductNotFoundException}, which
 * {@link GlobalExceptionHandler} already maps, and Spring resolves the handler via the exception's
 * type hierarchy - so this test is what actually proves that resolution happens, not a direct
 * unit-test call to the handler method. No site-products REST controller exists yet (planned for
 * phase 5d), so this uses {@link SiteProductNotFoundThrowingTestController}, a minimal throwaway
 * controller purely to exercise real dispatch - delete both once a real controller endpoint
 * covers the same mapping.
 */
@WebMvcTest(controllers = SiteProductNotFoundThrowingTestController.class,
        excludeAutoConfiguration = {org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class})
class SiteProductNotFoundExceptionMappingTest {

    @Autowired
    private MockMvc mockMvc;

    // Auto-detected as a Filter bean by the @WebMvcTest slice (mirrors AuthControllerTest);
    // none of these are exercised by this controller, they just need to construct.
    @MockBean private JwtService jwtService;
    @MockBean private UserService userService;
    @MockBean private AuthorizedSiteContextFactory authorizedSiteContextFactory;

    @Test
    void siteProductNotFoundException_mapsToHttp404() throws Exception {
        mockMvc.perform(get("/test/site-product-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("No site_products row for this pair"));
    }
}
