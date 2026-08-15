package com.adags.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

@AnalyzeClasses(packages = "com.adags", importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundaryTest {

    @ArchTest
    static final ArchRule modules_do_not_depend_on_each_other =
            slices().matching("com.adags.modules.(*)..").should().notDependOnEachOther();

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
