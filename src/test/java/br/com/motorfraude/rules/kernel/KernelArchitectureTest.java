package br.com.motorfraude.rules.kernel;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "br.com.motorfraude.rules.kernel", importOptions = ImportOption.DoNotIncludeTests.class)
class KernelArchitectureTest {

    @ArchTest
    static final ArchRule modeloNaoConheceCel = noClasses().that().resideInAPackage("..kernel.model..")
            .should().dependOnClassesThat().resideInAnyPackage("dev.cel..", "..kernel.compiler..", "..kernel.catalog..");

    @ArchTest
    static final ArchRule catalogoNaoConheceCel = noClasses().that().resideInAPackage("..kernel.catalog..")
            .should().dependOnClassesThat().resideInAnyPackage("dev.cel..", "..kernel.compiler..");

    @ArchTest
    static final ArchRule avaliacaoNaoConheceCel = noClasses().that().resideInAPackage("..kernel.evaluation..")
            .should().dependOnClassesThat().resideInAnyPackage("dev.cel..", "..kernel.compiler..");

    @ArchTest
    static final ArchRule semFramework = noClasses().that().resideInAPackage("..kernel..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta..", "org.apache.kafka..");
}
