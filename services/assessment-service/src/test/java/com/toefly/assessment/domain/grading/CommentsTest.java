package com.toefly.assessment.domain.grading;

import static org.assertj.core.api.Assertions.assertThat;

import com.toefly.assessment.domain.evaluation.AnnotationStatus;
import com.toefly.assessment.domain.evaluation.InlineAnnotation;
import com.toefly.assessment.domain.evaluation.MistakeCategory;
import com.toefly.assessment.domain.evaluation.Severity;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CommentsTest {

    static final Taxonomy TAXONOMY = new Taxonomy(Map.of(
            "GRAMMAR.ARTICLES",
            new Taxonomy.Entry("GRAMMAR.ARTICLES", "Articles", "ROUNDUP5.U10", "Round-Up Unit 10: Nouns – Articles"),
            "GRAMMAR.OTHER",
            new Taxonomy.Entry("GRAMMAR.OTHER", "Other grammar", null, null),
            "SPELLING.COMMON",
            new Taxonomy.Entry("SPELLING.COMMON", "Spelling", null, null),
            "SPELLING.OTHER",
            new Taxonomy.Entry("SPELLING.OTHER", "Other spelling", null, null)));

    static final String TEXT = "People lives longer  because of “modern” medicine. People lives in cities.";

    static RawComment comment(String quote, int occurrence, MistakeCategory category, String sub, Severity severity) {
        return new RawComment(quote, occurrence, category, sub, severity, "Explain " + quote, "fix");
    }

    @Test
    @DisplayName("AC-11.03.3 quotes are found exactly, by occurrence, after normalising and fuzzily")
    void locatesQuotes() {
        assertThat(QuoteLocator.locate(TEXT, "People lives", 1)).contains(new QuoteLocator.Range(0, 12));
        assertThat(QuoteLocator.locate(TEXT, "People lives", 2)).contains(new QuoteLocator.Range(51, 63));
        assertThat(QuoteLocator.locate(TEXT, "People lives", 9)).contains(new QuoteLocator.Range(0, 12));
        assertThat(QuoteLocator.locate(TEXT, "longer because of \"modern\"", 1))
                .contains(new QuoteLocator.Range(13, 40));
        assertThat(QuoteLocator.locate(TEXT, "People lives in citeis", 1)).isPresent();
        assertThat(QuoteLocator.locate(TEXT, "completely different words here", 1))
                .isEmpty();
        assertThat(QuoteLocator.locate(TEXT, "  ", 1)).isEmpty();
        assertThat(QuoteLocator.locate(TEXT, "zzz", 1)).isEmpty();
        assertThat(QuoteLocator.bounded("abc", 0, 3, "abd", -1)).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-11.03.3 comments of both passes merge without overlaps and keep SPELLING")
    void mergesPasses() {
        List<RawComment> primary = List.of(
                comment("lives", 1, MistakeCategory.GRAMMAR, "ARTICLES", Severity.MAJOR),
                comment("not in the text at all", 1, MistakeCategory.GRAMMAR, "UNKNOWN", Severity.MINOR));
        List<RawComment> secondary = List.of(
                comment("People lives", 1, MistakeCategory.GRAMMAR, "GRAMMAR.ARTICLES", Severity.CRITICAL),
                comment("cities", 1, MistakeCategory.SPELLING, "COMMON", Severity.MAJOR),
                comment("medicine", 1, MistakeCategory.SPELLING, "COMMON", Severity.MINOR),
                comment("not in the text at all", 1, MistakeCategory.GRAMMAR, "UNKNOWN", Severity.MAJOR));
        List<InlineAnnotation> merged = CommentMerger.merge(TEXT, primary, secondary, TAXONOMY);
        assertThat(merged)
                .extracting(InlineAnnotation::anchorQuote)
                .containsExactly("lives", "not in the text at all", "cities");
        assertThat(merged.getFirst().grammarTopic()).isEqualTo("ROUNDUP5.U10");
        assertThat(merged.get(1).anchored()).isFalse();
        assertThat(merged.get(1).subcategory()).isEqualTo("GRAMMAR.OTHER");
        assertThat(merged.get(2).category()).isEqualTo(MistakeCategory.SPELLING);
        assertThat(merged).allMatch(a -> a.status() == AnnotationStatus.OPEN);
        assertThat(CommentMerger.anchoredShare(TEXT, primary)).isEqualTo(0.5);
        assertThat(CommentMerger.anchoredShare(TEXT, List.of())).isEqualTo(1.0);
    }

    @Test
    @DisplayName("AC-11.05.2 comments of the previous version become Fixed or Still present")
    void carriesStatuses() {
        String v1 = "People lives longer. Their is a reason. I like it.";
        String v2 = "People live longer. Their is a reason. I really like it.";
        List<InlineAnnotation> previous = CommentMerger.merge(
                v1,
                List.of(
                        comment("lives", 1, MistakeCategory.GRAMMAR, "ARTICLES", Severity.MAJOR),
                        comment("Their", 1, MistakeCategory.SPELLING, "COMMON", Severity.MAJOR),
                        comment("I like it", 1, MistakeCategory.GRAMMAR, "OTHER", Severity.MINOR),
                        comment("nowhere to be found", 1, MistakeCategory.SPELLING, "COMMON", Severity.MINOR)),
                List.of(),
                TAXONOMY);
        List<InlineAnnotation> current = CommentMerger.merge(
                v2,
                List.of(comment("I really like it", 1, MistakeCategory.GRAMMAR, "OTHER", Severity.MINOR)),
                List.of(),
                TAXONOMY);
        List<InlineAnnotation> carried = AnnotationCarryOver.carry(v1, v2, previous, current);
        assertThat(carried)
                .extracting(InlineAnnotation::status)
                .containsExactly(
                        AnnotationStatus.FIXED,
                        AnnotationStatus.STILL_PRESENT,
                        AnnotationStatus.STILL_PRESENT,
                        AnnotationStatus.FIXED);
        assertThat(AnnotationCarryOver.carry(v1, "", previous.subList(0, 1), List.of())
                        .getFirst()
                        .status())
                .isEqualTo(AnnotationStatus.FIXED);
    }
}
