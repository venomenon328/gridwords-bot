package de.venomenon.gridwordsbot;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
        packages = "de.venomenon.gridwordsbot",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String[] FORBIDDEN_FRAMEWORK_AND_ADAPTER_PACKAGES = {
            "org.springframework..",
            "net.dv8tion.jda..",
            "jakarta.persistence..",
            "javax.persistence..",
            "org.hibernate..",
            "..adapter.."
    };

    @ArchTest
    static final ArchRule domainDoesNotDependOnFrameworksOrAdapters = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(FORBIDDEN_FRAMEWORK_AND_ADAPTER_PACKAGES);

    @ArchTest
    static final ArchRule parsersOnlyDependOnDomainAndJdk = noClasses()
            .that().resideInAPackage("..parser..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "net.dv8tion.jda..",
                    "jakarta.persistence..",
                    "javax.persistence..",
                    "org.hibernate..",
                    "..application..",
                    "..port..",
                    "..adapter..");

    @ArchTest
    static final ArchRule portsDoNotDependOnAdaptersOrPersistenceFrameworks = noClasses()
            .that().resideInAPackage("..port..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..adapter..", "org.springframework..", "org.springframework.data..",
                    "org.springframework.jdbc..", "jakarta.persistence..", "javax.persistence..", "org.hibernate..");

    @ArchTest
    static final ArchRule persistenceTypesStayInPersistenceAdapter = noClasses()
            .that().doNotResideInAPackage("..adapter.persistence..")
            .should().dependOnClassesThat().resideInAnyPackage("jakarta.persistence..", "org.springframework.data.repository..");
}