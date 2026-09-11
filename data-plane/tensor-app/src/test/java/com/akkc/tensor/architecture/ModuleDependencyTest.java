package com.akkc.tensor.architecture;

import com.akkc.tensor.plugin.api.DataSourcePlugin;
import com.akkc.tensor.plugin.api.DatasetAdapter;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ModuleDependencyTest {

    @Test
    void controllersDependOnUseCasesInsteadOfBusinessCollaborators() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.akkc.tensor");

        noClasses().that().haveSimpleNameEndingWith("Controller")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..core.registry..", "..core.catalog..", "..core.persistence..",
                        "..plugin.tushare..", "..plugin.fixture..")
                .orShould().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
                .orShould().dependOnClassesThat().areAssignableTo(DataSourcePlugin.class)
                .orShould().dependOnClassesThat().areAssignableTo(DatasetAdapter.class)
                .check(classes);
        noClasses().that().haveNameMatching(".*Controller\\$.*")
                .should().beAssignableTo(TensorException.class)
                .allowEmptyShould(true).check(classes);
    }

    @Test
    void coreAndOperationLoggingAreIndependentOfHttp() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.akkc.tensor");

        noClasses().that().resideInAPackage("..core..")
                .or().haveFullyQualifiedName("com.akkc.tensor.observability.OperationLogger")
                .or().haveFullyQualifiedName("com.akkc.tensor.observability.DownloadTaskOperationLogger")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.akkc.tensor.web..", "org.springframework.web..",
                        "org.springframework.http..", "jakarta.servlet..")
                .check(classes);
        noClasses().that().haveNameMatching("com\\.akkc\\.tensor\\.core\\.download\\.task\\.DownloadTaskObserver(\\$.*)?")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.slf4j..", "io.micrometer..", "com.akkc.tensor.observability..")
                .check(classes);
    }

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
