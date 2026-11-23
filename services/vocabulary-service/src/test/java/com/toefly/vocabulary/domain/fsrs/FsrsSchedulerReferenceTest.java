package com.toefly.vocabulary.domain.fsrs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * DoD of phase 8: the scheduler gives the stability, difficulty, state and interval of the reference implementation
 * (py-fsrs 5.1.3 with the FSRS-5 defaults, fuzz off, 365 days at most) for fixed grade sequences. The vectors and
 * their generator are in {@code src/test/resources/fsrs/}.
 */
class FsrsSchedulerReferenceTest {

    private final FsrsScheduler scheduler =
            new FsrsScheduler(FsrsParameters.v1().withoutFuzz());

    @TestFactory
    @DisplayName("AC-15.03.3: FSRS-5 matches the reference vectors of py-fsrs 5.1.3")
    Stream<DynamicTest> matchesReference() throws IOException {
        JsonNode vectors;
        try (InputStream in = getClass().getResourceAsStream("/fsrs/py-fsrs-5.1.3-vectors.json")) {
            vectors = JsonMapper.builder().build().readTree(in);
        }
        List<DynamicTest> tests = new ArrayList<>();
        for (Map.Entry<String, JsonNode> sequence : vectors.properties()) {
            tests.add(DynamicTest.dynamicTest(sequence.getKey(), () -> replay(sequence.getValue())));
        }
        return tests.stream();
    }

    private void replay(JsonNode rows) {
        CardMemory card = CardMemory.fresh(Instant.parse("2026-11-02T09:00:00Z"));
        int index = 0;
        for (JsonNode row : rows) {
            Instant at = Instant.parse(row.path("at").asString().replace("+00:00", "Z"));
            ScheduledReview result =
                    scheduler.review(card, Rating.ofGrade(row.path("rating").asInt()), at, ZoneOffset.UTC, 7);
            String where = "review " + index;
            assertThat(result.after().state().name())
                    .as(where)
                    .isEqualTo(row.path("state").asString());
            assertThat(result.after().stability())
                    .as(where + " stability")
                    .isCloseTo(row.path("stability").asDouble(), within(1e-4));
            assertThat(result.after().difficulty())
                    .as(where + " difficulty")
                    .isCloseTo(row.path("difficulty").asDouble(), within(1e-4));
            assertThat(result.interval())
                    .as(where + " interval")
                    .isEqualTo(Duration.ofSeconds(row.path("intervalSeconds").asLong()));
            card = result.after();
            index++;
        }
    }
}
