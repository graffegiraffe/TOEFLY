package com.toefly.common.test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.EvaluationResult;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.GeneralCodingRules;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Architecture rules of every TOEFLY service (docs/04-architecture.md §12.5). A service test calls
 * {@code ToeflyArchitectureRules.check("com.toefly.<service>")}.
 */
public final class ToeflyArchitectureRules {

    private static final String KAFKA_LISTENER = "org.springframework.kafka.annotation.KafkaListener";
    private static final String PROCESSED_EVENT_GUARD = "com.toefly.common.outbox.ProcessedEventGuard";
    private static final String[] TRANSACTIONAL = {
        "org.springframework.transaction.annotation.Transactional", "jakarta.transaction.Transactional"
    };

    public static final ArchRule hexagonalLayers = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .withOptionalLayers(true)
            .layer("Domain")
            .definedBy("com.toefly.*.domain..")
            .layer("Application")
            .definedBy("com.toefly.*.application..")
            .layer("Adapter")
            .definedBy("com.toefly.*.adapter..")
            .layer("Config")
            .definedBy("com.toefly.*.config..")
            .whereLayer("Adapter")
            .mayOnlyBeAccessedByLayers("Config")
            .whereLayer("Application")
            .mayOnlyBeAccessedByLayers("Adapter", "Config")
            .whereLayer("Domain")
            .mayOnlyBeAccessedByLayers("Application", "Adapter", "Config")
            .as("hexagonalLayers");

    public static final ArchRule domainIsFrameworkFree = noClasses()
            .that()
            .resideInAPackage("com.toefly.*.domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "org.springframework..",
                    "jakarta.persistence..",
                    "org.apache.kafka..",
                    "tools.jackson..",
                    "com.fasterxml.jackson..")
            .as("domainIsFrameworkFree");

    public static final ArchRule adaptersAreIndependent = slices().matching("com.toefly.*.adapter.(*).(*)..")
            .should()
            .notDependOnEachOther()
            .as("adaptersAreIndependent");

    public static final ArchRule useCasesAreInterfaces = classes()
            .that()
            .haveSimpleNameEndingWith("UseCase")
            .should()
            .beInterfaces()
            .andShould()
            .resideInAPackage("..application.port.in..")
            .allowEmptyShould(true)
            .as("useCasesAreInterfaces");

    public static final ArchRule portsInPortOut = classes()
            .that()
            .haveSimpleNameEndingWith("Port")
            .should()
            .beInterfaces()
            .andShould()
            .resideInAPackage("..application.port.out..")
            .allowEmptyShould(true)
            .as("portsInPortOut");

    public static final ArchRule controllersOnlyCallUseCases = noClasses()
            .that()
            .resideInAPackage("..adapter.in.web..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..application.service..", "..application.port.out..", "..adapter.out..")
            .as("controllersOnlyCallUseCases");

    public static final ArchRule controllersAreNamedAndPlaced = classes()
            .that()
            .areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
            .should()
            .haveSimpleNameEndingWith("Controller")
            .andShould()
            .resideInAPackage("..adapter.in.web..")
            .allowEmptyShould(true)
            .as("controllersAreNamedAndPlaced");

    public static final ArchRule noDirectKafkaTemplate = noClasses()
            .that()
            .resideOutsideOfPackage("com.toefly.common..")
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("org.springframework.kafka.core.KafkaTemplate")
            .as("noDirectKafkaTemplate");

    public static final ArchRule noFieldInjection =
            GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION.as("noFieldInjection");

    public static final ArchRule transactionalOnlyInApplication = noMethods()
            .that()
            .areDeclaredInClassesThat()
            .resideOutsideOfPackage("..application.service..")
            .should()
            .beAnnotatedWith(TRANSACTIONAL[0])
            .orShould()
            .beAnnotatedWith(TRANSACTIONAL[1])
            .allowEmptyShould(true)
            .as("transactionalOnlyInApplication");

    public static final ArchRule transactionalClassesOnlyInApplication = noClasses()
            .that()
            .resideOutsideOfPackage("..application.service..")
            .should()
            .beAnnotatedWith(TRANSACTIONAL[0])
            .orShould()
            .beAnnotatedWith(TRANSACTIONAL[1])
            .as("transactionalClassesOnlyInApplication");

    public static final ArchRule listenersAreIdempotent = methods()
            .that()
            .areAnnotatedWith(KAFKA_LISTENER)
            .or()
            .areMetaAnnotatedWith(KAFKA_LISTENER)
            .should(beDeclaredInClassUsing(PROCESSED_EVENT_GUARD))
            .allowEmptyShould(true)
            .as("listenersAreIdempotent");

    public static final ArchRule noCycles = slices().matching("com.toefly.*.(*)..")
            .should()
            .beFreeOfCycles()
            .allowEmptyShould(true)
            .as("noCycles");

    public static final ArchRule noLombok = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAPackage("lombok..")
            .as("noLombok");

    public static final List<ArchRule> ALL = List.of(
            hexagonalLayers,
            domainIsFrameworkFree,
            adaptersAreIndependent,
            useCasesAreInterfaces,
            portsInPortOut,
            controllersOnlyCallUseCases,
            controllersAreNamedAndPlaced,
            noDirectKafkaTemplate,
            noFieldInjection,
            transactionalOnlyInApplication,
            transactionalClassesOnlyInApplication,
            listenersAreIdempotent,
            noCycles,
            noLombok);

    private ToeflyArchitectureRules() {}

    /** Checks every rule against the production classes of a service and reports all violations at once. */
    public static void check(String basePackage) {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(basePackage);
        String report = ALL.stream()
                .map(rule -> rule.evaluate(classes))
                .filter(EvaluationResult::hasViolation)
                .map(result -> result.getFailureReport().toString())
                .collect(Collectors.joining("\n\n"));
        if (!report.isEmpty()) {
            throw new AssertionError("TOEFLY architecture rules violated:\n" + report);
        }
    }

    private static ArchCondition<JavaMethod> beDeclaredInClassUsing(String className) {
        return new ArchCondition<>("be declared in a class that uses " + className) {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                JavaClass owner = method.getOwner();
                boolean uses = owner.getDirectDependenciesFromSelf().stream()
                        .anyMatch(dependency ->
                                dependency.getTargetClass().getName().equals(className));
                events.add(new SimpleConditionEvent(
                        method, uses, method.getFullName() + " is in a class that does not use " + className));
            }
        };
    }
}
