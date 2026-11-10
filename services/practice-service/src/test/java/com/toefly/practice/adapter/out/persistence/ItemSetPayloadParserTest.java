package com.toefly.practice.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.toefly.practice.application.port.in.ItemSetContent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ItemSetPayloadParserTest {

    private static ItemSetContent parse(String type) throws IOException {
        return ItemSetPayloadParser.parse(
                Files.readString(Path.of("src/test/resources/fixtures/item-sets/" + type + ".json")));
    }

    @Test
    @DisplayName("AC-07.01.1 a gap shows its first part and as many cells as letters are missing, not the letters")
    void completeTheWordsShowsTheGapLength() throws IOException {
        ItemSetContent content = parse("COMPLETE_THE_WORDS");

        Map<String, Object> gap = content.items().get(2);
        assertThat(gap).containsEntry("prefix", "wa").containsEntry("missingLetters", 3);
        assertThat(gap).doesNotContainKeys("suffix", "fullWord", "answerKey", "acceptedAnswers");
        assertThat((String) content.stimulus().get("markup")).contains("wa{_}").doesNotContain("ter}");
    }

    @Test
    @DisplayName("AC-09.01.1 the assembled sentence, the key and the distractor stay hidden")
    void buildASentenceHidesTheAnswer() throws IOException {
        Map<String, Object> item = parse("BUILD_A_SENTENCE").items().get(1);

        assertThat(item).containsKeys("contextLine", "template", "chunks");
        assertThat(item)
                .doesNotContainKeys("sentence", "answerKey", "acceptedKeys", "distractorChunkId", "explanation");
    }

    @Test
    @DisplayName("AC-08.01.1 options keep their text but lose the rationale; the transcript waits for the review")
    void listeningHidesRationalesAndTranscripts() throws IOException {
        ItemSetContent content = parse("LISTEN_CONVERSATION");

        assertThat(content.stimulus()).doesNotContainKey("transcript");
        assertThat(content.reviewStimulus()).containsKey("transcript");
        assertThat(content.items().get(1).toString())
                .doesNotContain("rationale")
                .contains("Pollen");
    }
}
