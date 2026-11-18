package com.toefly.assessment.domain.submission;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** One version of a Writing submission: the draft (1), Attempt 2 or Attempt 3 (docs/03-domain-model.md §5.2). */
public final class SubmissionVersion {

    private final UUID id;
    private final int versionNo;
    private SubmissionStatus status;
    private String text;
    private int wordCount;
    private int activeSeconds;
    private int overtimeSeconds;
    private @Nullable Instant submittedAt;
    private final boolean countsForEstimate;

    public SubmissionVersion(
            UUID id,
            int versionNo,
            SubmissionStatus status,
            String text,
            int wordCount,
            int activeSeconds,
            int overtimeSeconds,
            @Nullable Instant submittedAt,
            boolean countsForEstimate) {
        this.id = id;
        this.versionNo = versionNo;
        this.status = status;
        this.text = text;
        this.wordCount = wordCount;
        this.activeSeconds = activeSeconds;
        this.overtimeSeconds = overtimeSeconds;
        this.submittedAt = submittedAt;
        this.countsForEstimate = countsForEstimate;
    }

    static SubmissionVersion draft(int versionNo, String text) {
        return new SubmissionVersion(
                UUID.randomUUID(),
                versionNo,
                SubmissionStatus.DRAFT,
                text,
                ResponseText.wordCount(text),
                0,
                0,
                null,
                versionNo == 1);
    }

    void edit(String newText, int elapsedSeconds, int timeLimitSeconds) {
        requireDraft();
        this.text = newText;
        this.wordCount = ResponseText.wordCount(newText);
        int elapsed = Math.max(0, elapsedSeconds);
        if (timeLimitSeconds > 0) {
            this.activeSeconds = Math.min(elapsed, timeLimitSeconds);
            this.overtimeSeconds = Math.max(0, elapsed - timeLimitSeconds);
        } else {
            this.activeSeconds = elapsed;
            this.overtimeSeconds = 0;
        }
    }

    void submit(Instant now) {
        requireDraft();
        this.status = SubmissionStatus.SUBMITTED;
        this.submittedAt = now;
    }

    void moveTo(SubmissionStatus next) {
        this.status = next;
    }

    private void requireDraft() {
        if (status != SubmissionStatus.DRAFT) {
            throw new SubmissionRuleException("conflict", "Version " + versionNo + " is already submitted.");
        }
    }

    public UUID id() {
        return id;
    }

    public int versionNo() {
        return versionNo;
    }

    public SubmissionStatus status() {
        return status;
    }

    public String text() {
        return text;
    }

    public int wordCount() {
        return wordCount;
    }

    public int activeSeconds() {
        return activeSeconds;
    }

    public int overtimeSeconds() {
        return overtimeSeconds;
    }

    public @Nullable Instant submittedAt() {
        return submittedAt;
    }

    public boolean countsForEstimate() {
        return countsForEstimate;
    }
}
