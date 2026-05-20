package com.mirai.inventoryservice.auth;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Small SpEL-callable helper used by @PreAuthorize expressions that need to short-circuit
 * role checks in dev mode. Reference it as `@authGate.isDevMode()` inside annotations,
 * e.g. {@code @PreAuthorize("@authGate.isDevMode() or hasRole('ADMIN')")}.
 *
 * Returns true only when the "dev" profile is active — in prod the role check still rules.
 */
@Component("authGate")
public class AuthGate {

    private final boolean devMode;

    public AuthGate(Environment env) {
        this.devMode = env.acceptsProfiles(profiles -> profiles.test("dev"));
    }

    public boolean isDevMode() {
        return devMode;
    }
}
