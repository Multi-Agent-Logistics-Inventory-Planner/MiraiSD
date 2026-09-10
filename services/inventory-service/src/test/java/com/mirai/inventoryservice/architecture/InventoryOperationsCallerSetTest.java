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

import static org.junit.jupiter.api.Assertions.fail;

/**
 * .specs/phase-6-inventory/log.md T-7: pins the exact set of classes outside {@code inventory}
 * that reach {@code InventoryOperations}/{@code InventoryQueries} -- T-5's eight named external
 * callers ({@code ShipmentService}, {@code KujiBoxService}, {@code MachineDisplayService},
 * {@code ProductReportBundleService}, {@code ForecastService}, {@code AnalyticsService},
 * {@code AuditLogService}, {@code SalesRollupRecomputeService}). Mirrors
 * {@code CatalogEntityAccessCallerSetTest}'s role (AC-5b's precedent), but at class granularity
 * rather than per-method: {@code noProductionClassOutsideInventoryDependsOnInventoryInfrastructure}
 * already proves nobody outside {@code inventory} reaches the repositories directly; this test
 * proves the *facade* callers haven't silently drifted -- a brand new consumer, or one of the
 * eight quietly reverting to some other access path, both fail here.
 *
 * <p>Origins are compared by fully qualified name, not simple name: a same-named class in a
 * different package (e.g. a future {@code kuji.application.AnalyticsService}) must not silently
 * collapse into this test's existing {@code services.AnalyticsService} entry.
 */
class InventoryOperationsCallerSetTest {

    private static final String INVENTORY_OPERATIONS = "com.mirai.inventoryservice.inventory.application.InventoryOperations";
    private static final String INVENTORY_QUERIES = "com.mirai.inventoryservice.inventory.application.InventoryQueries";

    private static JavaClasses importedClasses;

    @BeforeAll
    static void importClasses() {
        importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.mirai.inventoryservice");
    }

    private static Set<String> expectedOrigins() {
        Set<String> expected = new HashSet<>();
        expected.add("com.mirai.inventoryservice.services.ShipmentService");
        expected.add("com.mirai.inventoryservice.services.KujiBoxService");
        expected.add("com.mirai.inventoryservice.services.MachineDisplayService");
        expected.add("com.mirai.inventoryservice.services.ProductReportBundleService");
        expected.add("com.mirai.inventoryservice.services.ForecastService");
        expected.add("com.mirai.inventoryservice.services.AnalyticsService");
        expected.add("com.mirai.inventoryservice.services.AuditLogService");
        expected.add("com.mirai.inventoryservice.analytics.application.SalesRollupRecomputeService");
        return expected;
    }

    /**
     * Scans every access shape ArchUnit tracks separately, matching
     * {@code CatalogEntityAccessCallerSetTest}'s discipline: a plain method call and a method
     * reference (e.g. {@code list.forEach(inventoryOperations::saveMovement)}) are different
     * {@code JavaAccess} subtypes, and only {@code getAccessesFromSelf()} catches both.
     */
    private static Set<String> actualOrigins() {
        Set<String> actual = new HashSet<>();
        for (JavaClass javaClass : importedClasses) {
            String module = moduleOf(javaClass);
            if ("inventory".equals(module)) {
                // InventoryOperations/InventoryQueries' own module -- not an external caller.
                continue;
            }
            for (JavaCodeUnit codeUnit : javaClass.getCodeUnits()) {
                for (JavaAccess<?> access : codeUnit.getAccessesFromSelf()) {
                    String targetOwner = access.getTargetOwner().getFullName();
                    if (INVENTORY_OPERATIONS.equals(targetOwner) || INVENTORY_QUERIES.equals(targetOwner)) {
                        actual.add(access.getOriginOwner().getFullName());
                    }
                }
            }
        }
        return actual;
    }

    private static String moduleOf(JavaClass javaClass) {
        String packageName = javaClass.getPackageName();
        String basePackage = "com.mirai.inventoryservice";
        if (!packageName.startsWith(basePackage + ".")) {
            return "";
        }
        String remainder = packageName.substring(basePackage.length() + 1);
        int firstDot = remainder.indexOf('.');
        return firstDot == -1 ? remainder : remainder.substring(0, firstDot);
    }

    @Test
    void inventoryOperationsAndQueriesCallersMatchT5Inventory() {
        Set<String> actual = actualOrigins();
        Set<String> expected = expectedOrigins();

        Set<String> unexpected = actual.stream()
                .filter(o -> !expected.contains(o))
                .collect(Collectors.toSet());
        Set<String> missing = expected.stream()
                .filter(o -> !actual.contains(o))
                .collect(Collectors.toSet());

        if (!unexpected.isEmpty() || !missing.isEmpty()) {
            fail("InventoryOperations/InventoryQueries callers drifted from T-5's recorded inventory "
                    + "(.specs/phase-6-inventory/log.md).\n"
                    + "Unexpected callers (new consumer, or a class that regained direct repository access "
                    + "instead of using the facade): " + unexpected + "\n"
                    + "Missing expected callers (origin removed or renamed without updating this test): "
                    + missing);
        }
    }
}
