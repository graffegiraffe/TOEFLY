package com.toefly.common.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class TopicNamesTest {

    @Test
    void buildsCanonicalTopicAndItsRetryAndDlq() {
        String topic = TopicNames.topic("practice", "attempt-scored", 1);

        assertThat(topic).isEqualTo("toefly.practice.attempt-scored.v1");
        assertThat(TopicNames.retry(topic)).isEqualTo("toefly.practice.attempt-scored.v1.retry");
        assertThat(TopicNames.dlq(topic)).isEqualTo("toefly.practice.attempt-scored.v1.dlq");
    }

    @Test
    void buildsApicurioReferences() {
        String topic = "toefly.content-factory.item-set-generated.v1";

        assertThat(TopicNames.valueArtifactId(topic)).isEqualTo("toefly.content-factory.item-set-generated.v1-value");
        assertThat(TopicNames.dataSchema(topic, 3))
                .isEqualTo("apicurio://toefly/toefly.content-factory.item-set-generated.v1-value/3");
    }

    @Test
    void rejectsNonCanonicalNames() {
        assertThat(TopicNames.isValid("toefly.practice.AttemptScored.v1")).isFalse();
        assertThat(TopicNames.isValid("toefly.practice.attempt-scored")).isFalse();
        assertThat(TopicNames.isValid("toefly.practice.attempt-scored.v0")).isFalse();
        assertThatIllegalArgumentException().isThrownBy(() -> TopicNames.topic("Practice", "x", 1));
        assertThatIllegalArgumentException().isThrownBy(() -> TopicNames.topic("practice", "attempt-scored", 0));
        assertThatIllegalArgumentException().isThrownBy(() -> TopicNames.dlq("orders"));
    }
}
