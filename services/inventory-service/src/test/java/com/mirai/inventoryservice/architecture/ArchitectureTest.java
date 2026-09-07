package com.mirai.inventoryservice.architecture;

import static com.tngtech.archunit.library.freeze.FreezingArchRule.freeze;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.Repository;

class ArchitectureTest {

    private static final String BASE_PACKAGE = "com.mirai.inventoryservice";

    // The target domain modules from docs/specs/spring-domain-modular-monolith.md section 4.
    // "shared" is deliberately excluded: it is the one module every other module may depend on.
    private static final String[] BUSINESS_MODULES = {
        "catalog", "sites", "identity", "inventory", "transfers", "shipments", "displays",
        "kuji", "lootbox", "analytics", "notifications", "reviews", "audit"
    };

    private static JavaClasses importedClasses;

    @BeforeAll
    static void importClasses() {
        importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE_PACKAGE);
    }

    // Existing classes in these packages are a known, frozen baseline. Any class added to
    // one of them after this point is a new violation and fails the build; the migration
    // moves classes out of here into their owning domain package instead.
    @Test
    void legacyTechnicalLayerPackagesDoNotGrow() {
        ArchRule rule = noClasses()
                .should()
                .resideInAnyPackage(
                        BASE_PACKAGE + ".controllers..",
                        BASE_PACKAGE + ".services..",
                        BASE_PACKAGE + ".repositories..",
                        BASE_PACKAGE + ".models..",
                        BASE_PACKAGE + ".dtos..",
                        BASE_PACKAGE + ".converters..")
                .because("business code belongs in its owning domain package, not a legacy technical-layer package");

        freeze(rule).check(importedClasses);
    }

    // Amended for Phase 5a (docs: .specs/phase-5a-catalog-module-move/spec.md AC-3) to cover
    // domain-module repositories on both sides:
    //  - target: a repository is no longer only "anything under the legacy `repositories`
    //    package" -- a domain module's own repository (e.g. catalog.infrastructure.ProductRepository)
    //    must be caught too, or moving a repository out of `repositories..` would silently drop
    //    it out of this rule's coverage while the build stays green. Detected as: resides in the
    //    legacy `repositories` package (preserved exactly, since a couple of legacy repositories
    //    like LocationAggregateRepository/InventoryTotalsRepository are plain @Repository classes
    //    over EntityManager, not Spring Data interfaces), OR is assignable to Spring Data's
    //    Repository marker interface (catches real repository interfaces wherever they live,
    //    without misclassifying non-repository infrastructure -- identity.infrastructure also
    //    holds SupabaseAdminService, UserRoleConverter and SiteAccessAuthorizationFilter, none of
    //    which are repositories).
    //  - caller: a domain module's own `application`/`infrastructure` class reaching a repository
    //    (its own, or the legacy package) is allowed, matching how the legacy `services` package
    //    was already exempt. Cross-module infrastructure access (module A reaching module B's
    //    repository) is a different concern, addressed separately by Phase 5b's
    //    noModuleDependsOnAnotherModulesInfrastructure rule -- this rule only asks whether a
    //    repository was reached by something that isn't a service/application layer at all.
    @Test
    void repositoriesAreOnlyAccessedByServicesOrRepositories() {
        freeze(repositoryAccessRule()).check(importedClasses);
    }

    /**
     * Package-visible so {@code ArchitectureTestRepositoryRuleProbeTest} can evaluate the exact
     * unfrozen rule (not a re-typed copy) against fixture classes before the frozen store is
     * regenerated. See the amendment note above {@link #repositoriesAreOnlyAccessedByServicesOrRepositories()}.
     */
    static ArchRule repositoryAccessRule() {
        return classes()
                .that(new DescribedPredicate<JavaClass>(
                        "reside outside a services/repositories layer or a domain module's "
                                + "application/infrastructure package") {
                    @Override
                    public boolean test(JavaClass javaClass) {
                        return !isAllowedRepositoryCaller(javaClass);
                    }
                })
                .should(new ArchCondition<JavaClass>("not depend on a repository") {
                    @Override
                    public void check(JavaClass javaClass, ConditionEvents events) {
                        for (Dependency dependency : javaClass.getDirectDependenciesFromSelf()) {
                            JavaClass target = dependency.getTargetClass();
                            if (isRepositoryClass(target)) {
                                events.add(SimpleConditionEvent.violated(
                                        javaClass,
                                        dependency.getDescription()
                                                + " -- repository access must go through a service"
                                                + " or application layer, not a controller or DTO mapper"));
                            }
                        }
                    }
                })
                .allowEmptyShould(true);
    }

    private static boolean isAllowedRepositoryCaller(JavaClass javaClass) {
        String packageName = javaClass.getPackageName();
        if (isPackageOrSubpackageOf(packageName, BASE_PACKAGE + ".services")
                || isPackageOrSubpackageOf(packageName, BASE_PACKAGE + ".repositories")) {
            return true;
        }
        String module = moduleOf(javaClass);
        for (String candidate : BUSINESS_MODULES) {
            if (candidate.equals(module)) {
                return isPackageOrSubpackageOf(packageName, BASE_PACKAGE + "." + module + ".application")
                        || isPackageOrSubpackageOf(packageName, BASE_PACKAGE + "." + module + ".infrastructure");
            }
        }
        return false;
    }

    private static boolean isRepositoryClass(JavaClass target) {
        return isPackageOrSubpackageOf(target.getPackageName(), BASE_PACKAGE + ".repositories")
                || target.isAssignableTo(Repository.class);
    }

    /** Exact package match or a strict dot-delimited descendant -- never a substring match. */
    private static boolean isPackageOrSubpackageOf(String packageName, String rootPackage) {
        return packageName.equals(rootPackage) || packageName.startsWith(rootPackage + ".");
    }

    @Test
    void topLevelPackagesAreFreeOfCycles() {
        ArchRule rule = slices()
                .matching(BASE_PACKAGE + ".(*)..")
                .should()
                .beFreeOfCycles();

        freeze(rule).check(importedClasses);
    }

    // No legacy code has moved into the new domain module skeleton yet, so these three rules
    // have nothing to freeze: they apply in full from the first class placed in any module.

    @Test
    void domainDoesNotDependOnApiOrAnotherModulesInfrastructure() {
        ArchRule rule = classes()
                .that()
                .resideInAPackage(BASE_PACKAGE + "..domain..")
                .should(new ArchCondition<JavaClass>(
                        "not depend on any module's api package or another module's infrastructure package") {
                    @Override
                    public void check(JavaClass javaClass, ConditionEvents events) {
                        String originModule = moduleOf(javaClass);
                        for (Dependency dependency : javaClass.getDirectDependenciesFromSelf()) {
                            JavaClass target = dependency.getTargetClass();
                            String targetPackage = target.getPackageName();

                            if (isInSubpackage(targetPackage, "api")) {
                                events.add(SimpleConditionEvent.violated(
                                        javaClass,
                                        dependency.getDescription() + " -- domain must not depend on an api package"));
                            } else if (isInSubpackage(targetPackage, "infrastructure")
                                    && !moduleOf(target).equals(originModule)) {
                                events.add(SimpleConditionEvent.violated(
                                        javaClass,
                                        dependency.getDescription()
                                                + " -- domain must not depend on another module's infrastructure package"));
                            }
                        }
                    }
                })
                .allowEmptyShould(true);

        rule.check(importedClasses);
    }

    @Test
    void modulesDoNotDependOnAnotherModulesApi() {
        ArchRule rule = classes()
                .that()
                .resideInAnyPackage(businessModulePackages())
                .should(new ArchCondition<JavaClass>("not depend on another module's api package") {
                    @Override
                    public void check(JavaClass javaClass, ConditionEvents events) {
                        String originModule = moduleOf(javaClass);
                        for (Dependency dependency : javaClass.getDirectDependenciesFromSelf()) {
                            JavaClass target = dependency.getTargetClass();
                            String targetModule = moduleOf(target);

                            if (isInSubpackage(target.getPackageName(), "api") && !targetModule.equals(originModule)) {
                                events.add(SimpleConditionEvent.violated(
                                        javaClass,
                                        dependency.getDescription()
                                                + " -- a module must not depend on another module's api package"));
                            }
                        }
                    }
                })
                .allowEmptyShould(true);

        rule.check(importedClasses);
    }

    @Test
    void sharedDoesNotDependOnBusinessModules() {
        ArchRule rule = noClasses()
                .that()
                .resideInAPackage(BASE_PACKAGE + ".shared..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(businessModulePackages())
                .because("shared must not depend on a business module")
                .allowEmptyShould(true);

        rule.check(importedClasses);
    }

    private static String[] businessModulePackages() {
        String[] packages = new String[BUSINESS_MODULES.length];
        for (int i = 0; i < BUSINESS_MODULES.length; i++) {
            packages[i] = BASE_PACKAGE + "." + BUSINESS_MODULES[i] + "..";
        }
        return packages;
    }

    private static boolean isInSubpackage(String packageName, String subpackageSimpleName) {
        return packageName.equals(BASE_PACKAGE + "." + subpackageSimpleName)
                || packageName.contains("." + subpackageSimpleName + ".")
                || packageName.endsWith("." + subpackageSimpleName);
    }

    /** The first package segment after {@link #BASE_PACKAGE}, e.g. "catalog", "shared", "controllers". */
    private static String moduleOf(JavaClass javaClass) {
        String packageName = javaClass.getPackageName();
        if (!packageName.startsWith(BASE_PACKAGE + ".")) {
            return "";
        }
        String remainder = packageName.substring(BASE_PACKAGE.length() + 1);
        int firstDot = remainder.indexOf('.');
        return firstDot == -1 ? remainder : remainder.substring(0, firstDot);
    }
}
