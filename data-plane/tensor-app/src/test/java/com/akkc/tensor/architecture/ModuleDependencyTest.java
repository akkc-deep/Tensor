package com.akkc.tensor.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ModuleDependencyTest {

    @Test
    void enforces_module_dependency_direction() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.akkc.tensor");

        ArchRule pluginApi = noClasses().that().resideInAPackage("..plugin.api..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.akkc.tensor.core..", "com.akkc.tensor.plugin.tushare..",
                        "com.akkc.tensor.plugin.fixture..", "com.akkc.tensor.app..");
        ArchRule core = noClasses().that().resideInAPackage("..core..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.akkc.tensor.plugin.tushare..", "com.akkc.tensor.plugin.fixture..",
                        "com.akkc.tensor.app..");
        ArchRule tushare = noClasses().that().resideInAPackage("..plugin.tushare..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.akkc.tensor.core..", "com.akkc.tensor.plugin.fixture..",
                        "com.akkc.tensor.app..");
        ArchRule fixture = noClasses().that().resideInAPackage("..plugin.fixture..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.akkc.tensor.plugin.tushare..", "com.akkc.tensor.app..");

        pluginApi.allowEmptyShould(true).check(classes);
        core.allowEmptyShould(true).check(classes);
        tushare.allowEmptyShould(true).check(classes);
        fixture.allowEmptyShould(true).check(classes);
    }
}
