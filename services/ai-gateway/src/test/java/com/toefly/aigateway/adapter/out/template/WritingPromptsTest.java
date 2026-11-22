package com.toefly.aigateway.adapter.out.template;

import static org.assertj.core.api.Assertions.assertThat;

import com.toefly.aigateway.adapter.out.schema.JsonOutputSchemaAdapter;
import com.toefly.aigateway.application.port.out.PromptSourcePort;
import com.toefly.aigateway.domain.prompt.PromptVersion;
import com.toefly.aigateway.domain.routing.AiUseCase;
import com.toefly.aigateway.domain.safety.GuardPolicy;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("prompts of WritingGrader (docs/06-ai-agent.md §5.4)")
class WritingPromptsTest {

    static final String OUTPUT = """
            {"criteria": {
               "taskAchievement": {"level": 4, "note": "Clear position."},
               "development": {"level": 3, "note": "One example."},
               "organization": {"level": 4, "note": "Logical."},
               "languageRange": {"level": 3, "note": "Some variety."},
               "accuracy": {"level": 3, "note": "Several errors."},
               "conventions": {"level": 4, "note": "Builds on a post."}},
             "score": 4, "scoreRationale": "Relevant and clear.", "bulletCoverage": [],
             "comments": [{"quote": "people lives", "occurrence": 1, "category": "GRAMMAR",
               "subcategory": "SUBJECT_VERB_AGREEMENT", "severity": "MAJOR", "comment": "Plural subject.",
               "suggestion": "people live"}],
             "strengths": ["Clear position"], "priorities": ["Check verb forms"], "integrityFlags": []}
            """;

    @Test
    @DisplayName(
            "every writing prompt version loads with the output schema, the guard of submissions and their eval suites")
    void loadsWritingPrompts() {
        List<PromptSourcePort.Source> sources = new PromptFileSource("classpath:prompts", Clock.systemUTC()).load();
        List<PromptVersion> writing = sources.stream()
                .map(PromptSourcePort.Source::version)
                .filter(v -> v.key().startsWith("writing.grade."))
                .toList();
        assertThat(writing)
                .extracting(v -> v.key() + ":" + v.version())
                .containsExactlyInAnyOrder(
                        "writing.grade.email:1", "writing.grade.discussion:1", "writing.grade.discussion:2");
        JsonOutputSchemaAdapter schemas = new JsonOutputSchemaAdapter();
        for (PromptVersion version : writing) {
            assertThat(version.useCase()).isEqualTo(AiUseCase.WRITING_GRADING);
            assertThat(version.guardPolicy()).isEqualTo(GuardPolicy.STUDENT_SUBMISSION);
            assertThat(version.untrusted()).containsExactly("responseText");
            assertThat(version.user()).contains("$responseText$", "$anchors$", "$previousVersionBlock$");
            assertThat(schemas.validate(version.outputSchema(), schemas.parse(OUTPUT)))
                    .isEmpty();
            assertThat(schemas.validate(
                            version.outputSchema(), schemas.parse(OUTPUT.replace("\"score\": 4", "\"score\": 7"))))
                    .isNotEmpty();
        }
    }
}
