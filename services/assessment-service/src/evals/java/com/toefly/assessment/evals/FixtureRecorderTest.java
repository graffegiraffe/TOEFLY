package com.toefly.assessment.evals;

import static org.assertj.core.api.Assertions.assertThat;

import com.toefly.assessment.adapter.out.client.ContentTaskAdapter;
import com.toefly.assessment.application.port.out.TaskContent;
import com.toefly.assessment.application.service.GradingInput;
import com.toefly.assessment.application.service.GradingResult;
import com.toefly.assessment.application.service.WritingGrader;
import com.toefly.assessment.domain.submission.ResponseText;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import tools.jackson.databind.JsonNode;

/**
 * Records the answers of the live ai-gateway for the synthetic essays of the component test (docs/11 §7, docs/12
 * phase 5b decision 5). The recordings replace the fixtures in {@code src/test/resources/ai-fixtures/recorded/}.
 *
 * <pre>./mvnw -Pevals -pl services/assessment-service test -Dtest=FixtureRecorderTest -Devals.record=true</pre>
 */
@EnabledIfSystemProperty(named = "evals.record", matches = "true")
class FixtureRecorderTest {

    static final UUID SUBMISSION = UUID.fromString("0a0b0c0d-0000-4000-8000-00000000f1c5");

    @Test
    @DisplayName("records passes A and B for the draft and Attempt 2 of the fixture essay")
    void record() throws Exception {
        Path fixtures = EvalSupport.module().resolve("src/test/resources/ai-fixtures");
        JsonNode essays = EvalDataset.JSON.readTree(Files.readString(fixtures.resolve("essays.json")));
        JsonNode set = EvalDataset.JSON.readTree(Files.readString(fixtures.resolve(essays.path("itemSet").asString())));
        TaskContent task = ContentTaskAdapter.task(UUID.fromString(set.path("id").asString()), set);
        WritingGrader grader = EvalSupport.grader(LiveGateway.fromEnvironment(fixtures.resolve("recorded")));
        GradingInput.Previous previous = null;
        for (JsonNode version : essays.path("versions")) {
            String text = ResponseText.normalize(version.path("text").asString());
            int no = version.path("versionNo").asInt();
            GradingResult result = grader.grade(new GradingInput(
                    SUBMISSION, no, EvalRunner.EVAL_USER, task, text, ResponseText.wordCount(text), 540, previous));
            System.out.printf("v%d: %.1f %s %d comments%n", no, result.finalScore(), result.passes(), result.annotations().size());
            previous = new GradingInput.Previous(no, result.finalScore(), List.of());
        }
        assertThat(Files.list(fixtures.resolve("recorded"))).isNotEmpty();
    }
}
