package com.adags.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.assertj.core.api.Assertions.assertThat;

@AnalyzeClasses(packages = "com.adags", importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundaryTest {

    // root CLAUDE.md "Modules": one public facade interface each — the one
    // sanctioned cross-module dependency. A facade lives directly under
    // com.adags.modules.<name> (not ..domain.., ..application.. or
    // ..infrastructure..) and is named *Facade, e.g. PatientFacade.
    // Public types declared directly in that interface are also part of
    // its contract (e.g. BillingFacade's catalogue request/response types).
    private static final DescribedPredicate<JavaClass> IS_A_MODULE_FACADE = DescribedPredicate.describe(
            "is a module facade or its public member type",
            javaClass -> isModuleFacade(javaClass)
                    || (javaClass.getModifiers().contains(JavaModifier.PUBLIC)
                        && javaClass.getEnclosingClass().map(ModuleBoundaryTest::isModuleFacade).orElse(false)));

    private static boolean isModuleFacade(JavaClass javaClass) {
        return javaClass.isInterface()
                && javaClass.getModifiers().contains(JavaModifier.PUBLIC)
                && javaClass.getSimpleName().endsWith("Facade")
                && javaClass.getPackageName().matches("com\\.adags\\.modules\\.[^.]+");
    }

    @ArchTest
    static void facadeExceptionOnlyAllowsThePublicContract(JavaClasses classes) {
        for (String name : new String[]{"com.adags.modules.billing.BillingFacade",
                "com.adags.modules.billing.BillingFacade$CatalogueKind",
                "com.adags.modules.billing.BillingFacade$UnclassifiedCatalogueEntry"}) {
            assertThat(IS_A_MODULE_FACADE.test(classes.get(name))).as(name).isTrue();
        }
        for (JavaClass javaClass : classes) {
            if (javaClass.getPackageName().matches("com\\.adags\\.modules\\.[^.]+\\.(domain|application|infrastructure|api)(\\..*)?")) {
                assertThat(IS_A_MODULE_FACADE.test(javaClass)).as(javaClass.getName()).isFalse();
            }
        }
    }

    @ArchTest
    static final ArchRule modules_do_not_depend_on_each_other =
            slices().matching("com.adags.modules.(*)..").should().notDependOnEachOther()
                    .ignoreDependency(DescribedPredicate.alwaysTrue(), IS_A_MODULE_FACADE);

    @ArchTest
    static final ArchRule shared_authz_does_not_depend_on_modules =
            noClasses().that().resideInAPackage("com.adags.shared.authz..")
                    .should().dependOnClassesThat().resideInAPackage("com.adags.modules..");

    @ArchTest
    static final ArchRule domain_packages_have_no_spring_stereotypes =
            noClasses().that().resideInAPackage("..domain..")
                    .should().beAnnotatedWith(Component.class)
                    .orShould().beAnnotatedWith(Service.class)
                    .orShould().beAnnotatedWith(Repository.class);
}
