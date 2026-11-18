package com.toefly.assessment.domain.submission;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Aggregate of a learner's work on one open item set (docs/03-domain-model.md §5.1–5.4). Up to three versions in
 * Practice; every change of a version bumps {@code version} for optimistic locking.
 */
public final class Submission {

    public static final int MAX_VERSIONS = 3;

    private final UUID id;
    private final UUID userId;
    private final UUID itemSetId;
    private final int itemSetVersion;
    private final TaskType taskType;
    private final AttemptMode mode;
    private final @Nullable UUID weekTaskId;
    private final @Nullable UUID examSessionId;
    private final Instant createdAt;
    private final List<SubmissionVersion> versions;
    private int version;

    public Submission(
            UUID id,
            UUID userId,
            UUID itemSetId,
            int itemSetVersion,
            TaskType taskType,
            AttemptMode mode,
            @Nullable UUID weekTaskId,
            @Nullable UUID examSessionId,
            Instant createdAt,
            List<SubmissionVersion> versions,
            int version) {
        if (versions.isEmpty()) {
            throw new IllegalArgumentException("a submission has at least one version");
        }
        this.id = id;
        this.userId = userId;
        this.itemSetId = itemSetId;
        this.itemSetVersion = itemSetVersion;
        this.taskType = taskType;
        this.mode = mode;
        this.weekTaskId = weekTaskId;
        this.examSessionId = examSessionId;
        this.createdAt = createdAt;
        this.versions = new ArrayList<>(versions);
        this.versions.sort(Comparator.comparingInt(SubmissionVersion::versionNo));
        this.version = version;
    }

    public static Submission start(
            UUID userId,
            UUID itemSetId,
            int itemSetVersion,
            TaskType taskType,
            AttemptMode mode,
            @Nullable UUID weekTaskId,
            @Nullable UUID examSessionId,
            Instant now) {
        return new Submission(
                UUID.randomUUID(),
                userId,
                itemSetId,
                itemSetVersion,
                taskType,
                mode,
                weekTaskId,
                examSessionId,
                now,
                List.of(SubmissionVersion.draft(1, "")),
                0);
    }

    /** Autosave of the current draft; the timer counts only for the first version (AC-11.05.1). */
    public SubmissionVersion saveDraft(String text, int elapsedSeconds) {
        SubmissionVersion current = current();
        current.edit(
                ResponseText.normalize(text),
                elapsedSeconds,
                current.versionNo() == 1 ? taskType.timeLimitSeconds() : 0);
        version++;
        return current;
    }

    /** The learner hands in the current version; an empty response is allowed in Exam only (INV-SUB-06). */
    public SubmissionVersion submit(Instant now) {
        SubmissionVersion current = current();
        if (current.status() != SubmissionStatus.DRAFT) {
            throw new SubmissionRuleException("conflict", "Version " + current.versionNo() + " is already submitted.");
        }
        if (mode == AttemptMode.PRACTICE && current.wordCount() < 1) {
            throw new SubmissionRuleException("submission-not-ready", "Write at least one word before you submit.");
        }
        current.submit(now);
        version++;
        return current;
    }

    /** SUBMITTED or EVALUATION_DEFERRED → EVALUATING when the command goes out (INV-SUB-07). */
    public void startEvaluation(int versionNo) {
        SubmissionVersion target = version(versionNo);
        SubmissionStatus status = target.status();
        if (status != SubmissionStatus.SUBMITTED
                && status != SubmissionStatus.EVALUATION_DEFERRED
                && status != SubmissionStatus.EVALUATING) {
            throw new SubmissionRuleException(
                    "conflict", "Version " + versionNo + " cannot be evaluated from " + status + ".");
        }
        move(target, SubmissionStatus.EVALUATING);
    }

    /** Whether a delivered command still has work to do (INV-SUB-08: only EVALUATING is overwritten). */
    public boolean awaitsEvaluation(int versionNo) {
        SubmissionStatus status = version(versionNo).status();
        return status == SubmissionStatus.EVALUATING || status == SubmissionStatus.EVALUATION_DEFERRED;
    }

    public void evaluated(int versionNo) {
        SubmissionVersion target = version(versionNo);
        if (target.status() == SubmissionStatus.TUTOR_REVIEWED) {
            return;
        }
        move(target, SubmissionStatus.EVALUATED);
    }

    public void defer(int versionNo) {
        SubmissionVersion target = version(versionNo);
        if (target.status() == SubmissionStatus.EVALUATING || target.status() == SubmissionStatus.SUBMITTED) {
            move(target, SubmissionStatus.EVALUATION_DEFERRED);
        }
    }

    /** A tutor scored the version by hand (docs/03-domain-model.md §5.4). */
    public void tutorReviewed(int versionNo) {
        SubmissionVersion target = version(versionNo);
        if (target.status() == SubmissionStatus.DRAFT) {
            throw new SubmissionRuleException("conflict", "A draft cannot be reviewed.");
        }
        move(target, SubmissionStatus.TUTOR_REVIEWED);
    }

    /** "Revise (Attempt N)": a new draft with the text of the evaluated version (INV-SUB-01..03). */
    public SubmissionVersion startRevision() {
        if (mode != AttemptMode.PRACTICE) {
            throw new SubmissionRuleException("conflict", "Exam responses cannot be revised.");
        }
        SubmissionVersion current = current();
        if (versions.size() >= MAX_VERSIONS) {
            throw new SubmissionRuleException("conflict", "A response has at most " + MAX_VERSIONS + " versions.");
        }
        if (!current.status().evaluated()) {
            throw new SubmissionRuleException("conflict", "Version " + current.versionNo() + " is not evaluated yet.");
        }
        SubmissionVersion next = SubmissionVersion.draft(current.versionNo() + 1, current.text());
        versions.add(next);
        version++;
        return next;
    }

    private void move(SubmissionVersion target, SubmissionStatus next) {
        target.moveTo(next);
        version++;
    }

    public SubmissionVersion current() {
        return versions.getLast();
    }

    public SubmissionVersion version(int versionNo) {
        return versions.stream()
                .filter(v -> v.versionNo() == versionNo)
                .findFirst()
                .orElseThrow(() -> new SubmissionRuleException("not-found", "No version " + versionNo + "."));
    }

    public @Nullable SubmissionVersion previous(int versionNo) {
        return versions.stream()
                .filter(v -> v.versionNo() == versionNo - 1)
                .findFirst()
                .orElse(null);
    }

    public SubmissionStatus status() {
        return current().status();
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public UUID itemSetId() {
        return itemSetId;
    }

    public int itemSetVersion() {
        return itemSetVersion;
    }

    public TaskType taskType() {
        return taskType;
    }

    public AttemptMode mode() {
        return mode;
    }

    public @Nullable UUID weekTaskId() {
        return weekTaskId;
    }

    public @Nullable UUID examSessionId() {
        return examSessionId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public List<SubmissionVersion> versions() {
        return List.copyOf(versions);
    }

    public int version() {
        return version;
    }
}
