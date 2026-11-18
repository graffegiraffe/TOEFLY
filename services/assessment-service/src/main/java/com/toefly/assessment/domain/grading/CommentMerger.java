package com.toefly.assessment.domain.grading;

import com.toefly.assessment.domain.evaluation.AnnotationStatus;
import com.toefly.assessment.domain.evaluation.InlineAnnotation;
import com.toefly.assessment.domain.evaluation.MistakeCategory;
import com.toefly.assessment.domain.evaluation.Severity;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Merging of the comments of two passes (docs/06-ai-agent.md §5.4.5): all comments of the pass closest to the final
 * score, then MAJOR and CRITICAL comments of the other pass whose quotes do not overlap the ones taken. Spelling
 * stays {@code SPELLING} (INV-SUB-12); a quote that is not found becomes a general comment without a range.
 */
public final class CommentMerger {

    public static final int MAX_ANNOTATIONS = 40;

    private CommentMerger() {}

    public static List<InlineAnnotation> merge(
            String text, List<RawComment> primary, List<RawComment> secondary, Taxonomy taxonomy) {
        List<InlineAnnotation> merged = new ArrayList<>();
        for (RawComment comment : primary) {
            if (merged.size() >= MAX_ANNOTATIONS) {
                break;
            }
            merged.add(anchor(text, comment, taxonomy));
        }
        for (RawComment comment : secondary) {
            if (merged.size() >= MAX_ANNOTATIONS) {
                break;
            }
            if (comment.severity() == Severity.MINOR) {
                continue;
            }
            InlineAnnotation candidate = anchor(text, comment, taxonomy);
            boolean overlaps = candidate.anchored()
                    && merged.stream().anyMatch(a -> a.overlaps(candidate.start(), candidate.end()));
            boolean duplicateGeneral = !candidate.anchored()
                    && merged.stream().anyMatch(a -> a.anchorQuote().equals(candidate.anchorQuote()));
            if (!overlaps && !duplicateGeneral) {
                merged.add(candidate);
            }
        }
        return merged;
    }

    /** Share of comments whose quote is found in the text; below 0.5 the pass is repeated once (§5.4.5). */
    public static double anchoredShare(String text, List<RawComment> comments) {
        if (comments.isEmpty()) {
            return 1;
        }
        long found = comments.stream()
                .filter(c ->
                        QuoteLocator.locate(text, c.quote(), c.occurrence()).isPresent())
                .count();
        return (double) found / comments.size();
    }

    static InlineAnnotation anchor(String text, RawComment comment, Taxonomy taxonomy) {
        Optional<QuoteLocator.Range> range = QuoteLocator.locate(text, comment.quote(), comment.occurrence());
        MistakeCategory category = comment.category();
        String subcategory = taxonomy.normalize(category, comment.subcategory());
        Taxonomy.Entry entry = taxonomy.entry(subcategory);
        String quote = range.map(r -> text.substring(r.start(), r.end())).orElse(comment.quote());
        return new InlineAnnotation(
                UUID.randomUUID(),
                range.map(QuoteLocator.Range::start).orElse(null),
                range.map(QuoteLocator.Range::end).orElse(null),
                quote,
                category,
                subcategory,
                comment.severity(),
                comment.comment(),
                comment.suggestion(),
                entry == null ? null : entry.unitCode(),
                entry == null ? null : entry.unitTitle(),
                AnnotationStatus.OPEN);
    }
}
