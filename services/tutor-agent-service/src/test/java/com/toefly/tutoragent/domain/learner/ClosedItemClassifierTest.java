package com.toefly.tutoragent.domain.learner;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ClosedItemClassifierTest {

    @ParameterizedTest(name = "{0}: {1} for {2} -> {3}")
    @CsvSource({
        "COMPLETE_THE_WORDS, enviroment, environment, SPELLING.COMMON",
        "COMPLETE_THE_WORDS, studies, study, GRAMMAR.NOUN_NUMBER",
        "COMPLETE_THE_WORDS, works, work, GRAMMAR.SUBJECT_VERB_AGREEMENT",
        "COMPLETE_THE_WORDS, walked, walking, GRAMMAR.VERB_TENSES",
        "COMPLETE_THE_WORDS, quickly, quick, VOCABULARY.WORD_FORM",
        "COMPLETE_THE_WORDS, house, garden, COMPREHENSION.WORD_COMPLETION",
        "BUILD_A_SENTENCE, b a c, Where did you go?, GRAMMAR.QUESTIONS_WORD_ORDER",
        "BUILD_A_SENTENCE, b a c, She left early., GRAMMAR.WORD_ORDER",
        "LISTEN_CHOOSE_RESPONSE, A, B, COMPREHENSION.RESPONSE_APPROPRIATENESS",
        "READ_ACADEMIC_PASSAGE, A, B, COMPREHENSION.DETAIL"
    })
    void classifiesWrongAnswers(String taskType, String answer, String key, String expected) {
        assertThat(ClosedItemClassifier.mistakeOf(taskType, answer, key)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "distance({0}, {1}) = {2}")
    @CsvSource({"abc, abc, 0", "abc, acb, 1", "kitten, sitting, 3", "'', abc, 3"})
    void damerauLevenshtein(String a, String b, int distance) {
        assertThat(ClosedItemClassifier.distance(a, b)).isEqualTo(distance);
    }
}
