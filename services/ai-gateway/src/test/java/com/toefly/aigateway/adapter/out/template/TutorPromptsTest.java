package com.toefly.aigateway.adapter.out.template;

import static org.assertj.core.api.Assertions.assertThat;

import com.toefly.aigateway.adapter.out.schema.JsonOutputSchemaAdapter;
import com.toefly.aigateway.application.port.out.PromptSourcePort;
import com.toefly.aigateway.domain.prompt.PromptVersion;
import com.toefly.aigateway.domain.routing.AiUseCase;
import com.toefly.aigateway.domain.safety.GuardPolicy;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("prompts of Toefly Tutor (docs/06-ai-agent.md §4, §5.3, §5.10)")
class TutorPromptsTest {

    static Map<String, PromptVersion> tutorPrompts() {
        return new PromptFileSource("classpath:prompts", Clock.systemUTC())
                .load().stream()
                        .map(PromptSourcePort.Source::version)
                        .filter(v -> v.key().startsWith("tutor."))
                        .collect(Collectors.toMap(PromptVersion::key, Function.identity()));
    }

    @Test
    @DisplayName("the chat, router, summary, weekly review and come-back prompts load with their use cases")
    void loadsTutorPrompts() {
        Map<String, PromptVersion> prompts = tutorPrompts();
        assertThat(prompts.keySet())
                .containsExactlyInAnyOrder(
                        "tutor.chat.system",
                        "tutor.router",
                        "tutor.chat.summarize",
                        "tutor.weekly-review",
                        "tutor.reengagement");
        assertThat(prompts.get("tutor.chat.system").useCase()).isEqualTo(AiUseCase.TUTOR_CHAT);
        assertThat(prompts.get("tutor.chat.system").guardPolicy()).isEqualTo(GuardPolicy.CHAT);
        assertThat(prompts.get("tutor.chat.system").system())
                .contains("$taskState$", "$screenContext$", "$retrievedMemories$", "$explanationLanguage$");
        assertThat(prompts.get("tutor.router").useCase()).isEqualTo(AiUseCase.TUTOR_ROUTER);
        assertThat(prompts.get("tutor.weekly-review").useCase()).isEqualTo(AiUseCase.WEEKLY_REVIEW);
        assertThat(prompts.get("tutor.reengagement").useCase()).isEqualTo(AiUseCase.REENGAGEMENT);
    }

    @Test
    @DisplayName("structured tutor prompts validate their answers against the output schemas")
    void schemasAcceptTheirAnswers() {
        Map<String, PromptVersion> prompts = tutorPrompts();
        JsonOutputSchemaAdapter schemas = new JsonOutputSchemaAdapter();
        Map<String, String> answers =
                Map.of("tutor.router", """
                {"intent":"EXPLAIN_MISTAKE","language":"EN","explicitUserRequest":{"addWord":false,"showAnswer":false,
                 "assignTask":false},"escalate":false,"needsScreenContext":true}
                """, "tutor.weekly-review", """
                {"headline":"Week 3: steady progress","summary":"You finished 12 of 15 tasks.","topMistakes":[
                 {"subcategory":"GRAMMAR.ARTICLES","count":4,"pattern":"Missing articles","example":{"wrong":"in library",
                 "right":"in the library"},"advice":"Study Round-Up Unit 10.","unitRefs":["ROUNDUP5.U10"]}],
                 "strengths":["Reading accuracy 80%"],"nextWeekFocus":[{"subcategory":"GRAMMAR.ARTICLES",
                 "why":"Four mistakes this week"}],"motivation":"Keep the same pace."}
                """, "tutor.reengagement", """
                {"title":"Your next step is ready","body":"Your last email scored 4.0. A 12-minute discussion task is next.",
                 "ctaLabel":"Start task","deepLink":"toefly://today"}
                """);
        answers.forEach((key, answer) -> {
            PromptVersion version = prompts.get(key);
            assertThat(schemas.validate(version.outputSchema(), schemas.parse(answer)))
                    .as(key)
                    .isEmpty();
        });
        assertThat(schemas.validate(
                        prompts.get("tutor.router").outputSchema(),
                        schemas.parse(answers.get("tutor.router").replace("EXPLAIN_MISTAKE", "SMALL_TALK"))))
                .isNotEmpty();
        assertThat(prompts.get("tutor.chat.system").outputSchema()).isNull();
        assertThat(List.of(prompts.get("tutor.router").untrusted())).isNotEmpty();
    }
}
