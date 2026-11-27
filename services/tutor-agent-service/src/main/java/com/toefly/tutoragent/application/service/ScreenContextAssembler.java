package com.toefly.tutoragent.application.service;

import com.toefly.tutoragent.application.port.NotSubmittedException;
import com.toefly.tutoragent.application.port.out.AssessmentPort;
import com.toefly.tutoragent.application.port.out.OwnerToolsPort;
import com.toefly.tutoragent.application.port.out.PracticePort;
import com.toefly.tutoragent.application.port.out.PracticeResultRepository;
import com.toefly.tutoragent.application.port.out.WeeklyReviewRepository;
import com.toefly.tutoragent.domain.chat.ChatContext;
import com.toefly.tutoragent.domain.chat.ChatContextType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Loads the screen the learner points at from its owner (docs/06 §4.7): the client sends a reference, never text, so
 * it cannot pass a key or another learner's data. An unsubmitted attempt or draft changes the type of the context.
 */
public class ScreenContextAssembler {

    static final int MAX_CHARS = 4800;
    static final int MAX_DRAFT_WORDS = 500;

    private final PracticePort practice;
    private final PracticeResultRepository results;
    private final AssessmentPort assessment;
    private final OwnerToolsPort owners;
    private final WeeklyReviewRepository reviews;

    public ScreenContextAssembler(
            PracticePort practice,
            PracticeResultRepository results,
            AssessmentPort assessment,
            OwnerToolsPort owners,
            WeeklyReviewRepository reviews) {
        this.practice = practice;
        this.results = results;
        this.assessment = assessment;
        this.owners = owners;
        this.reviews = reviews;
    }

    /**
     * @param context the resolved context (its type may become ATTEMPT_IN_PROGRESS or SUBMISSION_DRAFT)
     * @param text the block of the system prompt
     * @param taskWords keys the learner must find, for the leak detector
     * @param draft the learner's own draft, for the leak detector
     */
    public record Screen(ChatContext context, String text, List<String> taskWords, String draft) {}

    public Screen load(@Nullable ChatContext context, UUID userId, @Nullable String bearer) {
        if (context == null || context.type() == ChatContextType.GENERAL) {
            return new Screen(
                    ChatContext.general(), "The student opened the chat without a task on the screen.", List.of(), "");
        }
        try {
            return switch (context.type()) {
                case ATTEMPT_REVIEW, ATTEMPT_IN_PROGRESS -> attempt(context, userId, bearer);
                case SUBMISSION_REVIEW, SUBMISSION_DRAFT -> submission(context, userId, bearer);
                case WEEK -> week(context, userId, bearer);
                case WEEKLY_REVIEW -> weeklyReview(context, userId);
                default ->
                    new Screen(
                            context,
                            "Screen: " + context.type().name().toLowerCase().replace('_', ' ') + ".",
                            List.of(),
                            "");
            };
        } catch (RuntimeException unavailable) {
            if (unavailable instanceof NotSubmittedException) {
                throw unavailable;
            }
            return new Screen(context, "The data of the screen is unavailable right now.", List.of(), "");
        }
    }

    private Screen attempt(ChatContext context, UUID userId, @Nullable String bearer) {
        if (context.ref() == null) {
            return new Screen(context.withType(ChatContextType.ATTEMPT_IN_PROGRESS), unsubmitted(), List.of(), "");
        }
        PracticePort.AttemptReview review;
        try {
            review = practice.review(context.ref(), bearer);
        } catch (NotSubmittedException inProgress) {
            return new Screen(
                    context.withType(ChatContextType.ATTEMPT_IN_PROGRESS).withLabel(label(context, "Task in progress")),
                    unsubmitted(),
                    List.of(),
                    "");
        }
        if (!results.ownerOf(context.ref()).map(userId::equals).orElse(false)) {
            return new Screen(ChatContext.general(), "The referenced attempt is not available.", List.of(), "");
        }
        StringBuilder text =
                new StringBuilder("Submitted attempt " + review.attemptId() + " (" + review.taskType() + ").\n");
        for (PracticePort.ItemReview item : review.items()) {
            boolean focus = item.itemId().equals(context.itemId());
            if (context.itemId() != null && !focus) {
                continue;
            }
            text.append("Question ")
                    .append(item.ordinal())
                    .append(focus ? " (the student asks about it)" : "")
                    .append(": ")
                    .append(item.correct() ? "correct" : "wrong")
                    .append("; student answer: ")
                    .append(item.answer() == null ? "none" : item.answer())
                    .append("; correct answer: ")
                    .append(item.key() == null ? "?" : item.key());
            if (!item.correct() && item.explanation() != null) {
                text.append("; explanation: ").append(item.explanation());
            }
            text.append('\n');
        }
        return new Screen(
                context.withType(ChatContextType.ATTEMPT_REVIEW)
                        .withLabel(label(context, "Review: " + pretty(review.taskType()))),
                clip(text.toString()),
                List.of(),
                "");
    }

    private Screen submission(ChatContext context, UUID userId, @Nullable String bearer) {
        if (context.ref() == null) {
            return new Screen(context.withType(ChatContextType.SUBMISSION_DRAFT), unsubmitted(), List.of(), "");
        }
        AssessmentPort.Submission s = assessment.submission(context.ref(), null, bearer);
        if (!s.userId().equals(userId)) {
            return new Screen(ChatContext.general(), "The referenced work is not available.", List.of(), "");
        }
        AssessmentPort.Version latest =
                s.versions().isEmpty() ? null : s.versions().getLast();
        String body = latest == null ? "" : words(latest.text(), MAX_DRAFT_WORDS);
        if (latest == null || latest.status().equals("DRAFT")) {
            return new Screen(
                    context.withType(ChatContextType.SUBMISSION_DRAFT)
                            .withLabel(label(context, "Draft: " + pretty(s.taskType()))),
                    unsubmitted() + " Task type: " + pretty(s.taskType()) + ".",
                    List.of(),
                    body);
        }
        StringBuilder text = new StringBuilder("Submitted " + pretty(s.taskType()) + ", version " + latest.versionNo())
                .append(", status ")
                .append(latest.status());
        if (latest.finalScore() != null) {
            text.append(", AI estimate ").append(latest.finalScore()).append(" of 5");
        }
        text.append(".\nText:\n").append(body).append("\nComments:\n");
        latest.comments().stream()
                .limit(8)
                .forEach(c -> text.append("- \"")
                        .append(c.quote())
                        .append("\": ")
                        .append(c.comment())
                        .append(c.suggestion() == null ? "" : " -> " + c.suggestion())
                        .append('\n'));
        return new Screen(
                context.withType(ChatContextType.SUBMISSION_REVIEW)
                        .withLabel(label(context, "Review: " + pretty(s.taskType()))),
                clip(text.toString()),
                List.of(),
                "");
    }

    private Screen week(ChatContext context, UUID userId, @Nullable String bearer) {
        Map<String, Object> week = owners.currentWeek(userId, bearer);
        Object number = week.getOrDefault("weekNo", week.get("number"));
        String text = "Week " + number + " page; progress " + week.getOrDefault("progressPercent", "?") + "%.";
        return new Screen(context.withLabel(label(context, "Week " + number)), text, List.of(), "");
    }

    private Screen weeklyReview(ChatContext context, UUID userId) {
        return reviews.find(context.ref() == null ? new UUID(0, 0) : context.ref())
                .filter(r -> r.userId().equals(userId))
                .map(r -> new Screen(
                        context.withLabel(label(context, "Weekly review — Week " + r.weekNo())),
                        clip("Weekly review of week " + r.weekNo() + ": "
                                + r.text().headline() + ". " + r.text().summary()
                                + " Top mistakes: "
                                + r.text().topMistakes().stream()
                                        .map(m -> m.subcategory() + " x" + m.count())
                                        .collect(Collectors.joining(", "))),
                        List.<String>of(),
                        ""))
                .orElseGet(() -> new Screen(context, "The weekly review is not available.", List.of(), ""));
    }

    private static String unsubmitted() {
        return "The student is working on a task that is NOT submitted yet. Do not give answers, keys or a written response.";
    }

    private static String label(ChatContext context, String fallback) {
        return context.label() != null ? context.label() : fallback;
    }

    static String pretty(String taskType) {
        String words = taskType.replace('_', ' ').toLowerCase();
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    static String words(String text, int max) {
        String[] all = text.strip().split("\\s+");
        if (all.length <= max) {
            return text.strip();
        }
        List<String> kept = new ArrayList<>(List.of(all).subList(0, max));
        return String.join(" ", kept) + " ...";
    }

    private static String clip(String text) {
        return text.length() <= MAX_CHARS ? text : text.substring(0, MAX_CHARS) + " ...";
    }
}
