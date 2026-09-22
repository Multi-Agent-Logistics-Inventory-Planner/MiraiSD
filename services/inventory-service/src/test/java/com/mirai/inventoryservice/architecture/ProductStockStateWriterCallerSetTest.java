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
 * .specs/phase-6-inventory 6c, T-6c-15 (F-6c-4's Row 4 resolution): pins the exact set of classes
 * that call {@code catalog.application.ProductStockStateWriter} -- {@code products.quantity}/
 * {@code products.is_active} stay deliberately global (not site-scoped) until Phase 6's inventory
 * split and Phase 7/8's per-site assortment work land, so any new caller of this writer is a
 * signal that global activity/quantity semantics are being silently reinterpreted per-site
 * somewhere, which this record's AC-2/Row-4 resolution explicitly rejects. Mirrors
 * {@code InventoryOperationsCallerSetTest}'s discipline exactly (class-granularity, FQN
 * comparison, {@code getAccessesFromSelf()} to catch method references too).
 */
class ProductStockStateWriterCallerSetTest {

    private static final String PRODUCT_STOCK_STATE_WRITER =
            "com.mirai.inventoryservice.catalog.application.ProductStockStateWriter";

    private static JavaClasses importedClasses;

    @BeforeAll
    static void importClasses() {
        importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.mirai.inventoryservice");
    }

    private static Set<String> expectedOrigins() {
        Set<String> expected = new HashSet<>();
        expected.add("com.mirai.inventoryservice.inventory.application.StockMovementService");
        expected.add("com.mirai.inventoryservice.services.KujiBoxService");
        return expected;
    }

    private static Set<String> actualOrigins() {
        Set<String> actual = new HashSet<>();
        for (JavaClass javaClass : importedClasses) {
            if (javaClass.getFullName().equals(PRODUCT_STOCK_STATE_WRITER)) {
                continue;
            }
            for (JavaCodeUnit codeUnit : javaClass.getCodeUnits()) {
                for (JavaAccess<?> access : codeUnit.getAccessesFromSelf()) {
                    if (PRODUCT_STOCK_STATE_WRITER.equals(access.getTargetOwner().getFullName())) {
                        actual.add(access.getOriginOwner().getFullName());
                    }
                }
            }
        }
        return actual;
    }

    @Test
    void productStockStateWriterCallersMatchTheRecordedGlobalActivitySet() {
        Set<String> actual = actualOrigins();
        Set<String> expected = expectedOrigins();

        Set<String> unexpected = actual.stream()
                .filter(o -> !expected.contains(o))
                .collect(Collectors.toSet());
        Set<String> missing = expected.stream()
                .filter(o -> !actual.contains(o))
                .collect(Collectors.toSet());

        if (!unexpected.isEmpty() || !missing.isEmpty()) {
            fail("ProductStockStateWriter callers drifted from the recorded global activity/quantity "
                    + "set (.specs/phase-6-inventory/log.md T-6c-15, F-6c-4).\n"
                    + "Unexpected callers (a new consumer of global products.quantity/is_active writes, "
                    + "possibly reinterpreting them per-site): " + unexpected + "\n"
                    + "Missing expected callers (origin removed or renamed without updating this test): "
                    + missing);
        }
    }
}
