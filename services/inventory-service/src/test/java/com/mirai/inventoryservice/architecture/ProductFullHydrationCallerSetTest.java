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
 * Regression for the egress-audit fix (.specs/catalog-product-egress/spec.md AC-5): a full
 * product-list egress incident traced back to a new caller reaching for the obvious-looking
 * {@code ProductRepository.findAllWithCategories()} -- a full {@code Product} entity {@code JOIN
 * FETCH} across category/parent-category/parent-product -- when a slim projection
 * ({@code *AsListItems}, {@code findAllSettingsDefaults}, {@code findAllCatalogListItems}) already
 * covered every real caller's actual need. Eight sibling heavy queries and their {@code
 * ProductService} wrappers were dead and have been deleted; this pins the callers of the three
 * that remain so a *new* full-catalog/full-list hydration caller fails the build here instead of
 * silently reintroducing the same class of incident.
 *
 * <p>Deliberately scoped to list-returning {@code *WithCategories} methods, not the single-row
 * ones ({@code findByIdWithCategories}, {@code findBySkuWithCategories}, {@code
 * findByIdWithParent}, {@code findByIdWithChildren}): a single-product detail read hydrating one
 * row's associations is not the egress pattern this guard exists to catch, and those methods have
 * many legitimate callers across the codebase that would make a caller-set pin here noise, not
 * signal.
 */
class ProductFullHydrationCallerSetTest {

    private static final String PRODUCT_REPOSITORY =
            "com.mirai.inventoryservice.catalog.infrastructure.ProductRepository";

    private static final Set<String> GUARDED_METHODS = Set.of(
            "findAllWithCategories",
            "findByParentIdWithCategories",
            "findByPreferredSupplierIdWithCategories");

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
     * The three remaining full-hydration callers, each with a real reason not to use a slim
     * projection: {@code AnalyticsSeedService} is dev-profile-only seed data, and
     * {@code ProductDeletionCoordinator}/{@code SupplierService} both need genuine entity
     * navigation (children, preferred-supplier association), not a flat projection.
     */
    private static Set<Origin> expectedOrigins() {
        Set<Origin> expected = new HashSet<>();
        expected.add(new Origin("AnalyticsSeedService", "seedMonthlyRollups", "findAllWithCategories"));
        expected.add(new Origin("AnalyticsSeedService", "rollupCategoryDemand", "findAllWithCategories"));
        expected.add(new Origin("ProductDeletionCoordinator", "deleteProduct", "findByParentIdWithCategories"));
        expected.add(new Origin("SupplierService", "getProductsBySupplierId", "findByPreferredSupplierIdWithCategories"));
        return expected;
    }

    /**
     * Same access-shape scanning rationale as {@code CatalogEntityAccessCallerSetTest}: covers
     * method calls, method references, and constructor calls in one pass so a
     * {@code productRepository::findAllWithCategories} method reference isn't missed by scanning
     * only method calls.
     */
    private static Set<Origin> actualOrigins() {
        Set<Origin> actual = new HashSet<>();
        for (JavaClass javaClass : importedClasses) {
            for (JavaCodeUnit codeUnit : javaClass.getCodeUnits()) {
                for (JavaAccess<?> access : codeUnit.getAccessesFromSelf()) {
                    if (!PRODUCT_REPOSITORY.equals(access.getTargetOwner().getFullName())) {
                        continue;
                    }
                    String accessor = access.getTarget().getName();
                    if (!GUARDED_METHODS.contains(accessor)) {
                        continue;
                    }
                    String methodName = topLevelMethodName(codeUnit);
                    actual.add(new Origin(access.getOriginOwner().getSimpleName(), methodName, accessor));
                }
            }
        }
        return actual;
    }

    private static String topLevelMethodName(JavaCodeUnit codeUnit) {
        String name = codeUnit.getName();
        if (name.startsWith("lambda$")) {
            String rest = name.substring("lambda$".length());
            int lastDollar = rest.lastIndexOf('$');
            return lastDollar > 0 ? rest.substring(0, lastDollar) : rest;
        }
        return name;
    }

    @Test
    void fullHydrationCallersMatchTheReviewedSet() {
        Set<Origin> actual = actualOrigins();
        Set<Origin> expected = expectedOrigins();

        Set<Origin> unexpected = actual.stream()
                .filter(o -> !expected.contains(o))
                .collect(Collectors.toSet());
        Set<Origin> missing = expected.stream()
                .filter(o -> !actual.contains(o))
                .collect(Collectors.toSet());

        if (!unexpected.isEmpty() || !missing.isEmpty()) {
            fail("ProductRepository full-hydration callers drifted from the reviewed set "
                    + "(.specs/catalog-product-egress/spec.md AC-5). A new caller of "
                    + "findAllWithCategories/findByParentIdWithCategories/"
                    + "findByPreferredSupplierIdWithCategories reaches for a full JOIN FETCH "
                    + "across category/parent-category/parent-product -- use a slim projection "
                    + "(*AsListItems, findAllSettingsDefaults, findAllCatalogListItems, or a new "
                    + "purpose-built one) instead, unless this caller genuinely needs entity "
                    + "navigation the way the three reviewed callers do.\n"
                    + "Unexpected callers: " + unexpected + "\n"
                    + "Missing expected callers (origin removed or renamed without updating this test): "
                    + missing);
        }
    }
}
