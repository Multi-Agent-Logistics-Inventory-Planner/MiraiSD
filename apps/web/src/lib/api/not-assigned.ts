/**
 * Placeholder locationId the UI uses for "NOT_ASSIGNED" before a real location UUID is known
 * (`LocationSelector`'s NOT_ASSIGNED option, `product-form.tsx`'s initial-stock default). Every
 * caller resolves it to a real, site-scoped location via `resolveSiteLocationId`
 * (lib/api/locations.ts) or, for Kuji's still-legacy path, `resolveLocationId`
 * (lib/api/inventory.ts).
 *
 * Single shared copy (.specs/phase-6-inventory 6e, T-6e-7) - previously duplicated across
 * `inventory.ts`, `locations.ts` and `location-selector.tsx` to sidestep a module cycle
 * (`inventory.ts` imports `getLocations` from `locations.ts`, so `locations.ts` couldn't import
 * the constant back from `inventory.ts`). This module has no other dependency, so both sides
 * import it without creating one.
 */
export const NOT_ASSIGNED_VIRTUAL_ID = "__not_assigned__";
