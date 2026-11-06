package com.toefly.content.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class PayloadRedactorTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    @DisplayName("learners never get keys, explanations or transcripts")
    void stripsSecrets() {
        var payload = json.readTree("""
                {"stimulus":{"title":"T","transcript":[{"speaker":"A","text":"hi"}]},
                 "items":[{"ordinal":1,"prompt":"Q?","answerKey":"B","explanation":{"summary":"s"},
                           "options":[{"key":"A","text":"a","rationale":"no"},{"key":"B","text":"b","rationale":"yes"}]}]}
                """);

        String redacted = PayloadRedactor.redact(payload).toString();

        assertThat(redacted).contains("\"prompt\":\"Q?\"", "\"text\":\"a\"");
        assertThat(redacted).doesNotContain("answerKey", "explanation", "rationale", "transcript");
    }

    @Test
    @DisplayName("the hidden part of a Complete the Words gap is blanked")
    void blanksGaps() {
        var payload = json.readTree("{\"stimulus\":{\"markup\":\"The cl{imate} is wa{rm} today.\"},\"items\":[]}");

        assertThat(PayloadRedactor.redact(payload).toString()).contains("The cl{_} is wa{_} today.");
    }
}
