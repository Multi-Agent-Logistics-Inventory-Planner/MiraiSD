package com.mirai.inventoryservice.architecture;

import static com.tngtech.archunit.library.freeze.FreezingArchRule.freeze;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

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

    @Test
    void repositoriesAreOnlyAccessedByServicesOrRepositories() {
        ArchRule rule = noClasses()
                .that()
                .resideOutsideOfPackage(BASE_PACKAGE + ".services..")
                .and()
                .resideOutsideOfPackage(BASE_PACKAGE + ".repositories..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage(BASE_PACKAGE + ".repositories..")
                .because("repository access must go through a service, not a controller or DTO mapper");

        freeze(rule).check(importedClasses);
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
