package com.toefly.assessment.adapter.in.web;

import com.toefly.assessment.domain.submission.SubmissionRuleException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.jspecify.annotations.Nullable;

/** Opaque cursors of the lists: the offset of the next page, Base64. */
final class Cursors {

    private Cursors() {}

    static int offset(@Nullable String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int offset = Integer.parseInt(decoded.substring("o:".length()));
            if (!decoded.startsWith("o:") || offset < 0) {
                throw new IllegalArgumentException(cursor);
            }
            return offset;
        } catch (IllegalArgumentException | IndexOutOfBoundsException invalid) {
            throw new SubmissionRuleException("invalid-cursor", "The cursor is not valid.");
        }
    }

    static String next(int offset) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(("o:" + offset).getBytes(StandardCharsets.UTF_8));
    }
}
