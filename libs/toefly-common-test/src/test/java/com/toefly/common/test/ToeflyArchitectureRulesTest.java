package com.toefly.common.test;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import java.util.List;
import org.junit.jupiter.api.Test;

class ToeflyArchitectureRulesTest {

    private static final JavaClasses GOOD = new ClassFileImporter().importPackages("com.toefly.archgood");
    private static final JavaClasses BAD = new ClassFileImporter().importPackages("com.toefly.archbad");

    @Test
    void hexagonalServicePassesEveryRule() {
        List<String> violated = ToeflyArchitectureRules.ALL.stream()
                .filter(rule -> rule.evaluate(GOOD).hasViolation())
                .map(rule -> rule.getDescription() + ": " + rule.evaluate(GOOD).getFailureReport())
                .toList();

        assertThat(violated).isEmpty();
    }

    @Test
    void violationsAreReported() {
        List<String> violated = ToeflyArchitectureRules.ALL.stream()
                .filter(rule -> rule.evaluate(BAD).hasViolation())
                .map(ArchRule::getDescription)
                .toList();

        assertThat(violated)
                .contains(
                        "domainIsFrameworkFree",
                        "controllersAreNamedAndPlaced",
                        "noDirectKafkaTemplate",
                        "noFieldInjection",
                        "transactionalOnlyInApplication",
                        "listenersAreIdempotent");
    }
}
