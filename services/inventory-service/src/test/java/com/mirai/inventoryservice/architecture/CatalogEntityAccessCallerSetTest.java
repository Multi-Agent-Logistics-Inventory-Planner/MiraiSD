package com.mirai.inventoryservice.architecture;

import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class CatalogEntityAccessCallerSetTest {

    private static final String CATALOG_ENTITY_ACCESS =
            "com.mirai.inventoryservice.catalog.application.CatalogEntityAccess";

    private static JavaClasses importedClasses;

    @BeforeAll
    static void importClasses() {
        importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.mirai.inventoryservice");
    }

    private record Origin(String className, String methodName, String accessor) {
        @Override
        public String toString() {
            return className + "." + methodName + " -> " + accessor;
        }
    }

    /**
     * T-0's complete association-write inventory (docs: .specs/phase-5b-catalog-facade/log.md,
     * "11 genuine origins require CatalogEntityAccess" table), corrected by T-3's review of origin
     * #5 (log.md's T-3 section, "LocationInventoryService needed CatalogEntityAccess, not
     * CatalogQueries"). Every origin uses {@code getReference} — the facade's default for a caller
     * that has already established the product's existence earlier in the same method — except
     * {@code LocationInventoryService.addInventory}, which is the *first* existence check for that
     * id in its method (unlike every other origin) and so deliberately uses
     * {@code requireManagedProduct} instead; T-3 recorded this as a correction to T-0's origin #5
     * strategy note, not a new decision, and it is preserved here as the one documented exception,
     * not treated as drift. Origins #8 and #9 both live inside {@code KujiBoxService.openBox} (the
     * box's own product, and the tier loop's existing-linked-product branch), so they collapse to
     * one method-level entry here; every other origin is a distinct method.
     */
    private static Set<Origin> expectedOrigins() {
        Set<Origin> expected = new HashSet<>();
        expected.add(new Origin("MachineDisplayService", "setDisplay", "getReference"));
        expected.add(new Origin("MachineDisplayService", "setDisplayBatch", "getReference"));
        expected.add(new Origin("MachineDisplayService", "swapDisplay", "getReference"));
        expected.add(new Origin("MachineDisplayService", "batchSwapDisplay", "getReference"));
        expected.add(new Origin("LocationInventoryService", "addInventory", "requireManagedProduct"));
        expected.add(new Origin("ShipmentService", "createShipment", "getReference"));
        expected.add(new Origin("ShipmentService", "updateShipment", "getReference"));
        expected.add(new Origin("KujiBoxService", "openBox", "getReference"));
        expected.add(new Origin("KujiBoxService", "addTier", "getReference"));
        expected.add(new Origin("KujiBoxService", "patchTier", "getReference"));
        return expected;
    }

    /**
     * Scans every access shape ArchUnit tracks separately — {@code getAccessesFromSelf()} covers
     * method calls, constructor calls, method/constructor references, and field accesses in one
     * set. A plain {@code catalogEntityAccess.getReference(id)} call and a method-reference
     * equivalent such as {@code ids.stream().map(catalogEntityAccess::getReference)} produce
     * different {@code JavaAccess} subtypes ({@code JavaMethodCall} vs. {@code JavaMethodReference}
     * respectively) — scanning only {@code getMethodCallsFromSelf()} would silently miss the
     * latter, letting a real new consumer through this guard uncaught.
     */
    private static Set<Origin> actualOrigins() {
        Set<Origin> actual = new HashSet<>();
        for (JavaClass javaClass : importedClasses) {
            for (JavaCodeUnit codeUnit : javaClass.getCodeUnits()) {
                for (JavaAccess<?> access : codeUnit.getAccessesFromSelf()) {
                    if (!CATALOG_ENTITY_ACCESS.equals(access.getTargetOwner().getFullName())) {
                        continue;
                    }
                    if (CATALOG_ENTITY_ACCESS.equals(access.getOriginOwner().getFullName())) {
                        // CatalogEntityAccess's own constructor/methods accessing their own
                        // productRepository field -- an internal self-access, not an external
                        // consumer of the facade.
                        continue;
                    }
                    String accessor = access.getTarget().getName();
                    String methodName = topLevelMethodName(codeUnit);
                    actual.add(new Origin(access.getOriginOwner().getSimpleName(), methodName, accessor));
                }
            }
        }
        return actual;
    }

    /**
     * A call or reference inside a lambda (e.g. {@code orElseGet(() -> ...)}, common at every
     * genuine origin here) is reported by ArchUnit as originating from a synthetic {@code
     * lambda$enclosingMethod$N} method, not the enclosing method itself. Strip that down to the
     * enclosing method name so a lambda-wrapped access still matches its real origin method —
     * matching what a human reading T-0's table means by "this method calls CatalogEntityAccess",
     * regardless of whether the specific access happens to sit inside a lambda within it.
     */
    private static String topLevelMethodName(JavaCodeUnit codeUnit) {
        String name = codeUnit.getName();
        if (name.startsWith("lambda$")) {
            String rest = name.substring("lambda$".length());
            int lastDollar = rest.lastIndexOf('$');
            return lastDollar > 0 ? rest.substring(0, lastDollar) : rest;
        }
        return name;
    }

    /**
     * AC-5b (docs: .specs/phase-5b-catalog-facade/spec.md): asserts, across the *entire* compiled
     * production codebase (not just the classes a given unit test happens to exercise), that every
     * {@code CatalogEntityAccess} access site — call or method reference — is one of T-0's recorded
     * origins (as corrected by T-3 for origin #5), using the strategy assigned to it. This catches
     * two things a per-class unit test (like {@code KujiBoxServiceCatalogFacadeMigrationTest})
     * structurally cannot: a brand new consumer introduced anywhere else in the codebase (via a
     * plain call or a method reference), and a known origin whose production code has silently
     * switched to the other accessor method.
     */
    @Test
    void catalogEntityAccessCallersMatchT0Inventory() {
        Set<Origin> actual = actualOrigins();
        Set<Origin> expected = expectedOrigins();

        Set<Origin> unexpected = actual.stream()
                .filter(o -> !expected.contains(o))
                .collect(Collectors.toSet());
        Set<Origin> missing = expected.stream()
                .filter(o -> !actual.contains(o))
                .collect(Collectors.toSet());

        if (!unexpected.isEmpty() || !missing.isEmpty()) {
            fail("CatalogEntityAccess callers drifted from T-0's recorded inventory "
                    + "(.specs/phase-5b-catalog-facade/log.md).\n"
                    + "Unexpected callers (new consumer, or wrong accessor method): " + unexpected + "\n"
                    + "Missing expected callers (origin removed or renamed without updating this test): "
                    + missing);
        }
    }

    /**
     * {@code requireManagedProduct} has exactly one production caller today —
     * {@code LocationInventoryService.addInventory}, T-3's documented exception (see class
     * javadoc) — not zero. Asserted separately from the main equality check above so a future
     * *additional* caller of {@code requireManagedProduct} (a second exception nobody reviewed)
     * reads in the failure message as exactly that, rather than blending into a generic caller-set
     * diff.
     */
    @Test
    void requireManagedProductHasExactlyTheDocumentedT3Exception() {
        Set<Origin> callers = actualOrigins().stream()
                .filter(o -> "requireManagedProduct".equals(o.accessor()))
                .collect(Collectors.toSet());

        assertEquals(
                Set.of(new Origin("LocationInventoryService", "addInventory", "requireManagedProduct")),
                callers,
                "requireManagedProduct should have exactly one production caller: "
                        + "LocationInventoryService.addInventory (T-3's documented exception, "
                        + "log.md). Every other origin already establishes existence itself and "
                        + "must use getReference; a second requireManagedProduct caller needs the "
                        + "same review T-3 gave this one before being added here.");
    }
}
