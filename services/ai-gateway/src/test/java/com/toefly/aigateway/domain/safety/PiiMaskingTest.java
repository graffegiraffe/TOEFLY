package com.toefly.aigateway.domain.safety;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class PiiMaskingTest {

    static final JsonMapper JSON = JsonMapper.builder().build();

    static String mask(String text, List<String> hints, MaskingSession session, Set<String> allow) {
        List<PiiSpan> spans = new ArrayList<>(RegexPiiDetector.detect(text));
        spans.addAll(DictionaryPiiDetector.detect(text, hints));
        return PiiMasker.mask(text, spans, session, allow);
    }

    @TestFactory
    List<DynamicTest> regexAndDictionaryRemoveEveryEmailPhoneAndProfileName() throws IOException {
        List<DynamicTest> tests = new ArrayList<>();
        for (String line : Files.readAllLines(Path.of("src/test/resources/safety/pii-texts.jsonl"))) {
            JsonNode sample = JSON.readTree(line);
            tests.add(DynamicTest.dynamicTest(sample.path("id").asString(), () -> {
                List<String> hints = new ArrayList<>();
                sample.path("hints").forEach(h -> hints.add(h.asString()));
                MaskingSession session = new MaskingSession();
                String masked = mask(sample.path("text").asString(), hints, session, Set.of());
                for (JsonNode secret : sample.path("secrets")) {
                    assertThat(masked).doesNotContain(secret.asString());
                }
                for (JsonNode name : sample.path("names")) {
                    assertThat(masked.toLowerCase())
                            .doesNotContain(name.asString().toLowerCase());
                }
                assertThat(session.unmask(masked)).isEqualTo(sample.path("text").asString());
            }));
        }
        return tests;
    }

    @Test
    void onePersonKeepsOnePlaceholderInEveryCaseForm() {
        MaskingSession session = new MaskingSession();
        String masked = mask(
                "Письмо от Волковой. Волкова пишет, что Daria Volkova придёт.",
                List.of("Daria", "Volkova"),
                session,
                Set.of());

        assertThat(masked).isEqualTo("Письмо от [PERSON_1]. [PERSON_1] пишет, что [PERSON_2] [PERSON_1] придёт.");
    }

    @Test
    void namesOfTheTaskStayAndInventedPlaceholdersAreLeftAndCounted() {
        MaskingSession session = new MaskingSession();
        String masked = mask("Dear Ms. Carter, I am Alina.", List.of("Alina"), session, Set.of("Carter"));

        assertThat(masked).isEqualTo("Dear Ms. Carter, I am [PERSON_1].");
        assertThat(session.unmask("Hello [PERSON_1] and [PERSON_7]")).isEqualTo("Hello Alina and [PERSON_7]");
        assertThat(session.anomalies()).isEqualTo(1);
    }

    @Test
    void sessionSurvivesStorageWithStableNumbers() {
        MaskingSession first = new MaskingSession();
        mask("Write to alina.k@example.com, Alina", List.of("Alina"), first, Set.of());

        MaskingSession restored = MaskingSession.restore(first.entries());
        String next = mask("Alina again, and maria@example.com", List.of("Alina"), restored, Set.of());

        assertThat(next).isEqualTo("[PERSON_1] again, and [EMAIL_2]");
    }

    @Test
    void cardsNeedTheLuhnCheckAndUrlsAQuery() {
        String text = "Card 4111 1111 1111 1111, not 1234 5678 9012 3456; see https://toefly.app/help and "
                + "https://example.com/p?id=1";
        String masked = mask(text, List.of(), new MaskingSession(), Set.of());

        assertThat(masked).contains("[CARD_1]", "1234 5678 9012 3456", "https://toefly.app/help", "[URL_1]");
    }

    @Test
    void streamingUnmaskerHoldsASplitPlaceholder() {
        MaskingSession session = new MaskingSession();
        String masked = mask("I am Alina", List.of("Alina"), session, Set.of());
        assertThat(masked).isEqualTo("I am [PERSON_1]");
        StreamingUnmasker stream = new StreamingUnmasker(session);

        String shown = stream.accept("Hello, [PER")
                + stream.accept("SON_1]! Price [")
                + stream.accept("ok] done")
                + stream.finish();

        assertThat(shown).isEqualTo("Hello, Alina! Price [ok] done");
    }
}
