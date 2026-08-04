package de.venomenon.gridwordsbot;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import org.springframework.data.repository.Repository;

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
    static final ArchRule excuseDomainOnlyDependsOnDomainAndJdk = noClasses()
            .that().resideInAPackage("..domain.excuse..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..application..",
                    "..port..",
                    "..adapter..",
                    "..config..",
                    "org.springframework..",
                    "net.dv8tion.jda..",
                    "tools.jackson..",
                    "jakarta.persistence..",
                    "javax.persistence..",
                    "org.hibernate..");

    @ArchTest
    static final ArchRule recordDomainOnlyDependsOnDomainAndJdk = noClasses()
            .that().resideInAPackage("..domain.record..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..application..",
                    "..port..",
                    "..adapter..",
                    "..config..",
                    "org.springframework..",
                    "net.dv8tion.jda..",
                    "tools.jackson..",
                    "jakarta.persistence..",
                    "javax.persistence..",
                    "org.hibernate..");

    @ArchTest
    static final ArchRule excuseTypesDoNotUseGlobalParticipationCompatibility = noClasses()
            .that().haveSimpleNameStartingWith("Excuse")
            .should().dependOnClassesThat().haveFullyQualifiedName(
                    "de.venomenon.gridwordsbot.domain.model.ParticipationPeriod");

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
    static final ArchRule applicationDoesNotDependOnConfigAdaptersOrFrameworks = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..adapter..", "..config..", "org.springframework..", "org.springframework.jdbc..",
                    "net.dv8tion.jda..", "jakarta.persistence..", "javax.persistence..", "org.hibernate..");

    @ArchTest
    static final ArchRule reportingDomainAndApplicationStayFreeOfJdaJdbcAndJpa = noClasses()
            .that().resideInAnyPackage("..domain.reporting..", "..application.reporting..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "net.dv8tion.jda..", "org.springframework.jdbc..", "org.springframework.data..",
                    "jakarta.persistence..", "javax.persistence..", "org.hibernate..");

    @ArchTest
    static final ArchRule productionProjectionsDoNotUseGlobalParticipationPeriods = noClasses()
            .that().resideInAnyPackage("..application..", "..domain.reporting..", "..domain.status..")
            .should().dependOnClassesThat().haveFullyQualifiedName(
                    "de.venomenon.gridwordsbot.domain.model.ParticipationPeriod");

    @ArchTest
    static final ArchRule entitiesOnlyExistInPersistenceAdapters = classes()
            .that().areAnnotatedWith(Entity.class)
            .should().resideInAnyPackage("..adapter.persistence..").allowEmptyShould(true);

    @ArchTest
    static final ArchRule springDataRepositoriesOnlyExistInPersistenceAdapters = classes()
            .that().areAssignableTo(Repository.class)
            .should().resideInAnyPackage("..adapter.persistence..").allowEmptyShould(true);
}
