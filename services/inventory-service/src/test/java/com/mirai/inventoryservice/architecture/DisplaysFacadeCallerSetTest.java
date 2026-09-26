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
 * .specs/phase-7-displays/log.md T-3: pins the exact set of classes outside {@code displays} that
 * reach {@code DisplayQueries}/{@code DisplayOperations}. Mirrors
 * {@code InventoryOperationsCallerSetTest}'s role: a brand new consumer, or one of the recorded
 * callers quietly reverting to direct repository/entity access, both fail here.
 */
class DisplaysFacadeCallerSetTest {

    private static final String DISPLAY_QUERIES = "com.mirai.inventoryservice.displays.application.DisplayQueries";
    private static final String DISPLAY_OPERATIONS = "com.mirai.inventoryservice.displays.application.DisplayOperations";

    private static JavaClasses importedClasses;

    @BeforeAll
    static void importClasses() {
        importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.mirai.inventoryservice");
    }

    private static Set<String> expectedQueriesOrigins() {
        Set<String> expected = new HashSet<>();
        expected.add("com.mirai.inventoryservice.services.AnalyticsService");
        expected.add("com.mirai.inventoryservice.services.ProductReportBundleService");
        return expected;
    }

    private static Set<String> expectedOperationsOrigins() {
        Set<String> expected = new HashSet<>();
        expected.add("com.mirai.inventoryservice.services.KujiBoxService");
        return expected;
    }

    private static Set<String> actualOrigins(String targetFqn) {
        Set<String> actual = new HashSet<>();
        for (JavaClass javaClass : importedClasses) {
            String module = moduleOf(javaClass);
            if ("displays".equals(module)) {
                continue;
            }
            for (JavaCodeUnit codeUnit : javaClass.getCodeUnits()) {
                for (JavaAccess<?> access : codeUnit.getAccessesFromSelf()) {
                    if (targetFqn.equals(access.getTargetOwner().getFullName())) {
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
    void displayQueriesCallersMatchT3Inventory() {
        assertMatches(actualOrigins(DISPLAY_QUERIES), expectedQueriesOrigins(), "DisplayQueries");
    }

    @Test
    void displayOperationsCallersMatchT3Inventory() {
        assertMatches(actualOrigins(DISPLAY_OPERATIONS), expectedOperationsOrigins(), "DisplayOperations");
    }

    private static void assertMatches(Set<String> actual, Set<String> expected, String facadeName) {
        Set<String> unexpected = actual.stream()
                .filter(o -> !expected.contains(o))
                .collect(Collectors.toSet());
        Set<String> missing = expected.stream()
                .filter(o -> !actual.contains(o))
                .collect(Collectors.toSet());

        if (!unexpected.isEmpty() || !missing.isEmpty()) {
            fail(facadeName + " callers drifted from T-3's recorded inventory "
                    + "(.specs/phase-7-displays/log.md).\n"
                    + "Unexpected callers (new consumer, or a class that regained direct repository/entity "
                    + "access instead of using the facade): " + unexpected + "\n"
                    + "Missing expected callers (origin removed or renamed without updating this test): "
                    + missing);
        }
    }
}
