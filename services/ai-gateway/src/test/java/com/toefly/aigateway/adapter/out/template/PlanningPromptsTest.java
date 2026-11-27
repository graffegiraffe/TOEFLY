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

@DisplayName("prompt of WeekGenerator (docs/06-ai-agent.md §5.2)")
class PlanningPromptsTest {

    static final String OUTPUT = """
            {"days": [
               {"day": "MON", "tasks": [
                 {"candidateRef": "c01", "whyForYou": "Articles came up 4 times in your emails last week."},
                 {"candidateRef": "c105", "whyForYou": "Next unit of New Round-Up 5."}]},
               {"day": "TUE", "tasks": []}],
             "weekGoal": "Use articles correctly in every email.",
             "focusExplanation": "Most of last week's mistakes were articles, so the week starts with them."}
            """;

    @Test
    @DisplayName("planning.week.generate loads with WEEK_GENERATION, no guard and the WeekDraftOutput schema")
    void loadsWeekPrompt() {
        List<PromptVersion> week = new PromptFileSource("classpath:prompts", Clock.systemUTC())
                .load().stream()
                        .map(PromptSourcePort.Source::version)
                        .filter(v -> v.key().equals("planning.week.generate"))
                        .toList();

        assertThat(week).extracting(PromptVersion::version).containsExactly(1);
        PromptVersion version = week.getFirst();
        assertThat(version.useCase()).isEqualTo(AiUseCase.WEEK_GENERATION);
        assertThat(version.guardPolicy()).isEqualTo(GuardPolicy.NONE);
        assertThat(version.untrusted()).isEmpty();
        assertThat(version.evalSuite()).isEqualTo("week-generation");
        assertThat(version.user())
                .contains(
                        "$weekNumber$",
                        "$studyDays$",
                        "$minutesPerDay$",
                        "$sectionWeights$",
                        "$focus$",
                        "$previousWeekReview$",
                        "$mandatory$",
                        "$candidates$",
                        "$previousViolations$");

        JsonOutputSchemaAdapter schemas = new JsonOutputSchemaAdapter();
        assertThat(schemas.validate(version.outputSchema(), schemas.parse(OUTPUT)))
                .isEmpty();
        assertThat(schemas.validate(version.outputSchema(), schemas.parse(OUTPUT.replace("\"c01\"", "\"item-7\""))))
                .as("a ref outside the pool format")
                .isNotEmpty();
        assertThat(schemas.validate(version.outputSchema(), schemas.parse(OUTPUT.replace("\"TUE\"", "\"DAY2\""))))
                .as("a day outside MON..SUN")
                .isNotEmpty();
        assertThat(schemas.validate(version.outputSchema(), schemas.parse(OUTPUT.replace("\"weekGoal\"", "\"goal\""))))
                .as("a missing weekGoal")
                .isNotEmpty();
    }
}
