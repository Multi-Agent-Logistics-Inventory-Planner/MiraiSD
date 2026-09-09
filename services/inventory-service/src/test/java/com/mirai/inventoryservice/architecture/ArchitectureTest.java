package com.mirai.inventoryservice.architecture;

import static com.tngtech.archunit.library.freeze.FreezingArchRule.freeze;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

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
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;
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

    // Amended for Phase 6a (docs: .specs/phase-6-inventory/log.md, T-0) to add a third arm: a
    // plain class annotated @Repository that does not implement Spring Data's Repository marker
    // interface -- e.g. LocationAggregateRepository/InventoryTotalsRepository, which are
    // EntityManager-backed native-SQL classes, not Spring Data interfaces. The legacy-package arm
    // (first) currently catches both of those, but only because they still live in
    // `repositories..`; once Phase 6a relocates them out of that package -- InventoryTotalsRepository
    // into inventory.infrastructure (T-3), LocationAggregateRepository into sites.infrastructure
    // per R-1's cross-module-read-projection assignment -- they would be neither in the legacy
    // package nor assignable to Repository, silently dropping out of this rule's coverage while
    // the build stays green -- the same failure mode the class-level comment above already
    // documents for the first two arms. Checked against every current @Repository-annotated class
    // in the codebase (all in repositories.., catalog/identity/sites.infrastructure) to confirm
    // none is a non-repository class that this new arm would misclassify.
    private static boolean isRepositoryClass(JavaClass target) {
        return isPackageOrSubpackageOf(target.getPackageName(), BASE_PACKAGE + ".repositories")
                || target.isAssignableTo(Repository.class)
                || target.isAnnotatedWith(org.springframework.stereotype.Repository.class);
    }

    /** Exact package match or a strict dot-delimited descendant -- never a substring match. */
    private static boolean isPackageOrSubpackageOf(String packageName, String rootPackage) {
        return packageName.equals(rootPackage) || packageName.startsWith(rootPackage + ".");
    }

    // Phase 5b (docs: .specs/phase-5b-catalog-facade/spec.md AC-3). Deliberately does NOT reuse
    // modulesDoNotDependOnAnotherModulesApi's source selector (businessModulePackages() -- only
    // the named domain modules): every one of the twelve consumers this record migrates still
    // lives in legacy services/controllers, which are not in that list, so mirroring it would
    // select vacuously against exactly the classes this rule exists to catch. The source here is
    // instead "every class outside catalog" (legacy packages included), and the target is
    // catalog.infrastructure as a whole -- which already covers ProductRepository,
    // CategoryRepository, and SupplierRepository (AC-3b) without naming them individually, since
    // all three already live in that one package.
    //
    // T-6: freeze lifted -- all twelve production consumers are migrated (T-3/T-4/T-4b), so this
    // rule now runs live rather than against a frozen baseline. This is Phase 5's module-boundary
    // exit-gate proof (AC-8). The two remaining callers, DevSeedController and
    // AnalyticsSeedService, are exempted by class name (T-5/AC-6) -- dev-profile-only seeding code
    // with no production equivalent (docs/baseline/api-v1-map.md), not a seeding port for two
    // throwaway consumers. The exemption is by class name, not a package wildcard, so a new
    // production class reaching catalog.infrastructure still fails the build.
    @Test
    void noProductionClassOutsideCatalogDependsOnCatalogInfrastructure() {
        outsideCatalogToCatalogInfrastructureRule().check(importedClasses);
    }

    // AC-6: dev-profile-only seeding controllers/services exempted by name from
    // noProductionClassOutsideCatalogDependsOnCatalogInfrastructure. No production equivalent
    // exists for either class (docs/baseline/api-v1-map.md); building a seeding port for two
    // throwaway consumers is not worth the indirection.
    private static final String[] CATALOG_INFRASTRUCTURE_ACCESS_EXEMPTIONS = {
        BASE_PACKAGE + ".controllers.DevSeedController", BASE_PACKAGE + ".services.AnalyticsSeedService"
    };

    /**
     * Package-visible so {@code ArchitectureTestCatalogInfrastructureRuleProbeTest} can evaluate
     * the exact unfrozen rule (not a re-typed copy) against fixture classes before the frozen
     * store is regenerated, matching the probe discipline used for
     * {@link #repositoryAccessRule()} in Phase 5a.
     */
    static ArchRule outsideCatalogToCatalogInfrastructureRule() {
        return classes()
                .that(new DescribedPredicate<JavaClass>("reside outside catalog and are not exempted") {
                    @Override
                    public boolean test(JavaClass javaClass) {
                        return !"catalog".equals(moduleOf(javaClass)) && !isExemptFromCatalogInfrastructureRule(javaClass);
                    }
                })
                .should(new ArchCondition<JavaClass>("not depend on catalog.infrastructure") {
                    @Override
                    public void check(JavaClass javaClass, ConditionEvents events) {
                        for (Dependency dependency : javaClass.getDirectDependenciesFromSelf()) {
                            JavaClass target = dependency.getTargetClass();
                            if (isPackageOrSubpackageOf(target.getPackageName(), BASE_PACKAGE + ".catalog.infrastructure")) {
                                events.add(SimpleConditionEvent.violated(
                                        javaClass,
                                        dependency.getDescription()
                                                + " -- catalog.infrastructure must only be accessed from within"
                                                + " catalog, via CatalogQueries/CatalogPricing/CatalogCommands/"
                                                + "ProductStockStateWriter/CatalogEntityAccess"));
                            }
                        }
                    }
                })
                .allowEmptyShould(true);
    }

    private static boolean isExemptFromCatalogInfrastructureRule(JavaClass javaClass) {
        String name = javaClass.getName();
        for (String exempt : CATALOG_INFRASTRUCTURE_ACCESS_EXEMPTIONS) {
            if (exempt.equals(name)) {
                return true;
            }
        }
        return false;
    }

    // Replaces a FreezingArchRule-based `topLevelPackagesAreFreeOfCycles` cycle check (removed;
    // see git history and .specs/phase-5c-site-products/log.md). That rule froze the full,
    // multi-line cycle-path text (including specific example call-site edges, subject to
    // ArchUnit's own truncation) for the pre-existing catalog/dtos/exceptions/identity/models/
    // repositories/services/validation cyclic legacy component - dozens of such violations, each
    // spanning many lines. Auditing or hand-updating that text on a genuine review is impractical:
    // a single new class anywhere in that already-cyclic component can change which concrete
    // example edges ArchUnit happens to print for an unrelated cycle instance, without changing
    // which modules actually participate. (The underlying cycle computation itself is
    // deterministic for a fixed set of imported classes - verified by independently diffing raw,
    // sorted violation output across separate JVM runs. What is impractical is keeping a
    // hand-reviewable frozen *text* snapshot in sync with a violation this large and verbose.)
    //
    // This check instead asserts something coarser, exactly deterministic, and directly
    // reviewable: the checked-in set of distinct (source module -> target module) dependency
    // edges must not gain any edge that wasn't explicitly approved. A new cross-module cycle
    // cannot form without at least one new edge, so rejecting every unapproved new edge also
    // rejects every new cycle - PROVIDED module-dependency-edges-baseline.txt is kept pruned: if
    // module A genuinely stops depending on B, that line must be deleted from the baseline as
    // part of the same review, not just left in place. An unpruned baseline would let that edge
    // return later without review, silently reopening exactly the case this check exists to
    // catch - so the guarantee holds only as long as reviewers prune on removal, not automatically.
    //
    // A rejected addition here always means: either the new dependency needs a different
    // seam (a port/facade per docs/specs/spring-domain-modular-monolith.md section 7), or the
    // edge is real, intentional, and reviewed - in which case add the line to
    // module-dependency-edges-baseline.txt as part of that same review, recording why (see that
    // file's header for the `catalog -> sites` precedent from T-4).
    @Test
    void moduleDependencyEdgesMatchApprovedBaseline() throws IOException {
        Set<String> currentEdges = new TreeSet<>();
        for (JavaClass javaClass : importedClasses) {
            String sourceModule = moduleOf(javaClass);
            if (sourceModule.isEmpty()) {
                continue;
            }
            for (Dependency dependency : javaClass.getDirectDependenciesFromSelf()) {
                String targetModule = moduleOf(dependency.getTargetClass());
                if (targetModule.isEmpty() || targetModule.equals(sourceModule)) {
                    continue;
                }
                currentEdges.add(sourceModule + " -> " + targetModule);
            }
        }

        Set<String> approvedEdges = new TreeSet<>();
        try (InputStream in = ArchitectureTest.class.getClassLoader()
                .getResourceAsStream("module-dependency-edges-baseline.txt")) {
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    approvedEdges.add(trimmed);
                }
            }
        }

        Set<String> newUnapprovedEdges = new TreeSet<>(currentEdges);
        newUnapprovedEdges.removeAll(approvedEdges);

        assertThat(newUnapprovedEdges)
                .as("new module dependency edge(s) not in module-dependency-edges-baseline.txt -"
                        + " review and either introduce a proper seam instead, or add the"
                        + " reviewed edge to that file with a justification")
                .isEmpty();
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
