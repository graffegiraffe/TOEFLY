package com.toefly.assessment.domain.grading;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;
import com.toefly.assessment.domain.evaluation.AnnotationStatus;
import com.toefly.assessment.domain.evaluation.InlineAnnotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Status of the comments of version n once version n + 1 is evaluated (docs/03-domain-model.md §5.5): the range is
 * mapped through a Myers diff of the code units; unchanged text means STILL_PRESENT; changed text is STILL_PRESENT
 * only if the new evaluation has a comment of the same category within 20 characters, else FIXED.
 */
public final class AnnotationCarryOver {

    static final int NEAR = 20;

    private AnnotationCarryOver() {}

    public static List<InlineAnnotation> carry(
            String oldText, String newText, List<InlineAnnotation> previous, List<InlineAnnotation> current) {
        int[] map = positions(oldText, newText);
        List<InlineAnnotation> result = new ArrayList<>();
        for (InlineAnnotation annotation : previous) {
            if (!annotation.anchored()) {
                result.add(annotation.withStatus(stillMentioned(annotation, current)));
                continue;
            }
            int from = -1;
            int to = -1;
            for (int i = annotation.start(); i < annotation.end(); i++) {
                if (map[i] >= 0) {
                    if (from < 0) {
                        from = map[i];
                    }
                    to = map[i] + 1;
                }
            }
            AnnotationStatus status;
            if (from < 0) {
                status = AnnotationStatus.FIXED;
            } else if (newText.substring(from, to).equals(annotation.anchorQuote())) {
                status = AnnotationStatus.STILL_PRESENT;
            } else {
                int lo = from - NEAR;
                int hi = to + NEAR;
                boolean again =
                        current.stream().anyMatch(c -> c.category() == annotation.category() && c.overlaps(lo, hi));
                status = again ? AnnotationStatus.STILL_PRESENT : AnnotationStatus.FIXED;
            }
            result.add(annotation.withStatus(status));
        }
        return result;
    }

    private static AnnotationStatus stillMentioned(InlineAnnotation annotation, List<InlineAnnotation> current) {
        boolean same = current.stream()
                .anyMatch(c ->
                        c.category() == annotation.category() && c.subcategory().equals(annotation.subcategory()));
        return same ? AnnotationStatus.STILL_PRESENT : AnnotationStatus.FIXED;
    }

    /** For every UTF-16 unit of the old text its position in the new text, or -1 if it was deleted or changed. */
    static int[] positions(String oldText, String newText) {
        List<Character> a = chars(oldText);
        List<Character> b = chars(newText);
        Patch<Character> patch = DiffUtils.diff(a, b);
        int[] map = new int[oldText.length()];
        Arrays.fill(map, -1);
        int oldPos = 0;
        int newPos = 0;
        for (AbstractDelta<Character> delta : patch.getDeltas()) {
            int sourceAt = delta.getSource().getPosition();
            while (oldPos < sourceAt) {
                map[oldPos++] = newPos++;
            }
            oldPos += delta.getSource().size();
            newPos += delta.getTarget().size();
        }
        while (oldPos < oldText.length()) {
            map[oldPos++] = newPos++;
        }
        return map;
    }

    private static List<Character> chars(String text) {
        List<Character> chars = new ArrayList<>(text.length());
        for (int i = 0; i < text.length(); i++) {
            chars.add(text.charAt(i));
        }
        return chars;
    }
}
