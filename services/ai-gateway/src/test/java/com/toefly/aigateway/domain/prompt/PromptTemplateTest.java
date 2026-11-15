package com.toefly.aigateway.domain.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.toefly.aigateway.domain.routing.AiUseCase;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class PromptTemplateTest {

    static final PromptTemplate TEMPLATE =
            new PromptTemplate("writing.grade.email", AiUseCase.WRITING_GRADING, "", "assessment-service", 7, null, 0);

    @Test
    void canaryGoesToAStableShareOfLearners() {
        PromptTemplate canary = TEMPLATE.canary(8, 10);

        long onCanary = IntStream.range(0, 2000)
                .mapToObj(i -> UUID.nameUUIDFromBytes(("learner-" + i).getBytes()))
                .filter(user -> Integer.valueOf(8).equals(canary.versionFor(user)))
                .count();
        UUID someone = UUID.nameUUIDFromBytes("learner-1".getBytes());

        assertThat(onCanary).isBetween(140L, 260L);
        assertThat(canary.versionFor(someone)).isEqualTo(canary.versionFor(someone));
        assertThat(canary.versionFor(null)).isEqualTo(7);
    }

    @Test
    void activationEndsTheCanary() {
        PromptTemplate active = TEMPLATE.canary(8, 10).activate(8);

        assertThat(active.activeVersion()).isEqualTo(8);
        assertThat(active.canaryVersion()).isNull();
    }

    @Test
    void canaryNeedsAnActiveVersion() {
        PromptTemplate fresh = new PromptTemplate("x.y", AiUseCase.HINT, "", "svc", null, null, 0);

        assertThatThrownBy(() -> fresh.canary(1, 10)).isInstanceOf(PromptRuleException.class);
        assertThat(fresh.versionFor(UUID.randomUUID())).isNull();
    }
}
