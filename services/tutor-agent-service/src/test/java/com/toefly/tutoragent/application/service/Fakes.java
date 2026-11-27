package com.toefly.tutoragent.application.service;

import com.toefly.tutoragent.application.port.AiUnavailableException;
import com.toefly.tutoragent.application.port.out.AiPort;
import com.toefly.tutoragent.application.port.out.AssessmentPort;
import com.toefly.tutoragent.application.port.out.Cause;
import com.toefly.tutoragent.application.port.out.ClosedWeek;
import com.toefly.tutoragent.application.port.out.DeferredReviewRepository;
import com.toefly.tutoragent.application.port.out.EntitlementPort;
import com.toefly.tutoragent.application.port.out.LearnerEventPublisherPort;
import com.toefly.tutoragent.application.port.out.LearnerModelRepository;
import com.toefly.tutoragent.application.port.out.MistakeMemoryRepository;
import com.toefly.tutoragent.application.port.out.MistakeRepository;
import com.toefly.tutoragent.application.port.out.PracticePort;
import com.toefly.tutoragent.application.port.out.PracticeResultRepository;
import com.toefly.tutoragent.application.port.out.PublicationSchedule;
import com.toefly.tutoragent.application.port.out.ReviewEventPublisherPort;
import com.toefly.tutoragent.application.port.out.WeeklyReviewRepository;
import com.toefly.tutoragent.domain.learner.LearnerModel;
import com.toefly.tutoragent.domain.learner.MistakeRecord;
import com.toefly.tutoragent.domain.learner.Opportunity;
import com.toefly.tutoragent.domain.learner.Taxonomy;
import com.toefly.tutoragent.domain.review.WeeklyReview;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/** In-memory ports for scenario tests of the application services. */
final class Fakes {

    static final Instant NOW = Instant.parse("2026-11-22T12:00:00Z");

    private Fakes() {}

    static Taxonomy taxonomy() {
        Map<String, Taxonomy.Entry> entries = new LinkedHashMap<>();
        entries.put(
                "GRAMMAR.ARTICLES",
                new Taxonomy.Entry("GRAMMAR.ARTICLES", "Articles", "Артикли", "ROUNDUP5.U10", "Round-Up Unit 10"));
        entries.put("COMPREHENSION.DETAIL", new Taxonomy.Entry("COMPREHENSION.DETAIL", "Detail", "Деталь", null, null));
        entries.put(
                "COMPREHENSION.WORD_COMPLETION",
                new Taxonomy.Entry("COMPREHENSION.WORD_COMPLETION", "Word completion", "Слова", null, null));
        entries.put("SPELLING.COMMON", new Taxonomy.Entry("SPELLING.COMMON", "Spelling", "Орфография", null, null));
        return new Taxonomy(entries);
    }

    static Clock clock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    static final Transactions TX = new Transactions() {
        @Override
        public void run(Runnable work) {
            work.run();
        }

        @Override
        public <T> T call(Supplier<T> work) {
            return work.get();
        }
    };

    static final class Models implements LearnerModelRepository {
        final Map<UUID, LearnerModel> models = new HashMap<>();
        final Map<String, LearnerModel> snapshots = new HashMap<>();

        @Override
        public Optional<LearnerModel> find(UUID userId) {
            return Optional.ofNullable(models.get(userId));
        }

        @Override
        public void save(LearnerModel model) {
            models.put(model.userId(), model);
        }

        @Override
        public void snapshot(LearnerModel model, int weekNo) {
            snapshots.put(model.userId() + ":" + weekNo, model);
        }

        @Override
        public Optional<LearnerModel> snapshotOf(UUID userId, int weekNo) {
            return Optional.ofNullable(snapshots.get(userId + ":" + weekNo));
        }
    }

    static final class Mistakes implements MistakeRepository {
        final List<MistakeRecord> records = new ArrayList<>();
        final List<Opportunity> opportunities = new ArrayList<>();

        @Override
        public void append(UUID userId, Collection<MistakeRecord> r, Collection<Opportunity> o) {
            records.addAll(r);
            opportunities.addAll(o);
        }

        @Override
        public List<MistakeRecord> records(UUID userId, Instant since) {
            return records.stream()
                    .filter(r -> r.userId().equals(userId) && !r.createdAt().isBefore(since))
                    .toList();
        }

        @Override
        public List<Opportunity> opportunities(UUID userId, Instant since) {
            return List.copyOf(opportunities);
        }

        @Override
        public List<MistakeRecord> examples(UUID userId, String subcategory, Instant since, int limit) {
            return records.stream()
                    .filter(r -> r.subcategory().equals(subcategory))
                    .limit(limit)
                    .toList();
        }
    }

    static final class Memory implements MistakeMemoryRepository {
        final Map<UUID, String> pending = new LinkedHashMap<>();
        final Map<UUID, float[]> vectors = new HashMap<>();
        final Map<UUID, UUID> owners = new HashMap<>();
        final Map<UUID, String> subcategories = new HashMap<>();
        int failures;

        @Override
        public void enqueue(Collection<MistakeRecord> records) {
            records.forEach(r -> {
                pending.put(r.id(), r.memoryText());
                owners.put(r.id(), r.userId());
                subcategories.put(r.id(), r.subcategory());
            });
        }

        @Override
        public List<PendingMemory> pending(int limit) {
            return pending.entrySet().stream()
                    .filter(e -> !vectors.containsKey(e.getKey()))
                    .limit(limit)
                    .map(e -> new PendingMemory(e.getKey(), owners.get(e.getKey()), e.getValue()))
                    .toList();
        }

        @Override
        public void embedded(UUID recordId, float[] embedding) {
            vectors.put(recordId, embedding);
        }

        @Override
        public void failed(UUID recordId) {
            failures++;
        }

        @Override
        public List<MemoryHit> nearest(
                UUID userId, float[] query, int topK, double minSimilarity, Collection<String> excluded) {
            return vectors.keySet().stream()
                    .filter(id -> owners.get(id).equals(userId) && !excluded.contains(subcategories.get(id)))
                    .limit(topK)
                    .map(id -> new MemoryHit(id, subcategories.get(id), pending.get(id), 0.9, NOW))
                    .toList();
        }
    }

    static final class Publications implements PublicationSchedule {
        final Map<UUID, Instant> windows = new LinkedHashMap<>();

        @Override
        public void open(UUID userId, Instant dueAt) {
            windows.putIfAbsent(userId, dueAt);
        }

        @Override
        public List<UUID> due(Instant now, int limit) {
            return windows.entrySet().stream()
                    .filter(e -> !e.getValue().isAfter(now))
                    .map(Map.Entry::getKey)
                    .toList();
        }

        @Override
        public void close(UUID userId) {
            windows.remove(userId);
        }
    }

    static final class LearnerEvents implements LearnerEventPublisherPort {
        final List<LearnerModel> published = new ArrayList<>();

        @Override
        public void learnerModelUpdated(LearnerModel model, Cause cause) {
            published.add(model);
        }
    }

    static final class Results implements PracticeResultRepository {
        final Map<UUID, Result> rows = new LinkedHashMap<>();

        @Override
        public void save(Result result) {
            rows.put(result.sourceId(), result);
        }

        void own(UUID sourceId, UUID userId) {
            rows.put(sourceId, new Result(sourceId, userId, "READ_ACADEMIC_PASSAGE", 0.5, null, NOW));
        }

        @Override
        public java.util.Optional<UUID> ownerOf(UUID sourceId) {
            return java.util.Optional.ofNullable(rows.get(sourceId)).map(Result::userId);
        }

        @Override
        public List<Result> between(UUID userId, Instant from, Instant to) {
            return rows.values().stream()
                    .filter(r -> r.userId().equals(userId)
                            && !r.occurredAt().isBefore(from)
                            && !r.occurredAt().isAfter(to))
                    .toList();
        }
    }

    static final class Practice implements PracticePort {
        final Map<UUID, AttemptReview> reviews = new HashMap<>();

        @Override
        public AttemptReview review(UUID attemptId, @Nullable String bearer) {
            AttemptReview review = reviews.get(attemptId);
            if (review == null) {
                throw new com.toefly.tutoragent.application.port.NotSubmittedException("in progress");
            }
            return review;
        }
    }

    static final class Assessment implements AssessmentPort {
        final Map<UUID, Submission> submissions = new HashMap<>();
        Map<String, Double> bands = Map.of();

        @Override
        public Submission submission(UUID submissionId, @Nullable Integer version, @Nullable String bearer) {
            Submission s = submissions.get(submissionId);
            if (s == null) {
                throw new com.toefly.tutoragent.application.port.NotSubmittedException("missing");
            }
            return s;
        }

        @Override
        public Map<String, Double> bandEstimate(UUID userId, @Nullable String bearer) {
            return bands;
        }
    }

    static final class Reviews implements WeeklyReviewRepository {
        final Map<UUID, WeeklyReview> byWeek = new LinkedHashMap<>();

        @Override
        public void save(WeeklyReview review) {
            byWeek.put(review.weekId(), review);
        }

        @Override
        public Optional<WeeklyReview> find(UUID reviewId) {
            return byWeek.values().stream().filter(r -> r.id().equals(reviewId)).findFirst();
        }

        @Override
        public Optional<WeeklyReview> findByWeek(UUID weekId) {
            return Optional.ofNullable(byWeek.get(weekId));
        }

        @Override
        public List<WeeklyReview> list(UUID userId, @Nullable Integer beforeWeekNo, int limit) {
            return byWeek.values().stream()
                    .filter(r -> r.userId().equals(userId) && (beforeWeekNo == null || r.weekNo() < beforeWeekNo))
                    .sorted(Comparator.comparingInt(WeeklyReview::weekNo).reversed())
                    .limit(limit)
                    .toList();
        }

        @Override
        public Optional<WeeklyReview> latest(UUID userId, int beforeWeekNo) {
            return list(userId, beforeWeekNo, 1).stream().findFirst();
        }
    }

    static final class DeferredRows implements DeferredReviewRepository {
        final Map<UUID, Row> rows = new LinkedHashMap<>();

        record Row(ClosedWeek week, int attempts, Instant next) {}

        @Override
        public void defer(ClosedWeek week, int attempts, Instant nextAttemptAt, String error) {
            rows.put(week.weekId(), new Row(week, attempts, nextAttemptAt));
        }

        @Override
        public List<DeferredReviewRepository.Deferred> due(Instant now, int limit) {
            return rows.values().stream()
                    .filter(r -> !r.next().isAfter(now))
                    .map(r -> new DeferredReviewRepository.Deferred(r.week(), r.attempts()))
                    .toList();
        }

        @Override
        public void remove(UUID weekId) {
            rows.remove(weekId);
        }
    }

    static final class Rights implements EntitlementPort {
        final Set<String> denied = new HashSet<>();

        @Override
        public boolean has(UUID userId, String entitlement) {
            return !denied.contains(entitlement);
        }
    }

    static final class ReviewEvents implements ReviewEventPublisherPort {
        final List<WeeklyReview> published = new ArrayList<>();

        @Override
        public void weeklyReviewGenerated(WeeklyReview review, Cause cause) {
            published.add(review);
        }
    }

    /** Answers completions from a queue; an empty queue means AI is unavailable. */
    static final class Ai implements AiPort {
        final Deque<Object> answers = new ArrayDeque<>();
        final List<CompletionCall> calls = new ArrayList<>();
        boolean embeddingsDown;

        void answer(Map<String, Object> output) {
            answers.add(output);
        }

        void answerText(String text) {
            answers.add(text);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Completion complete(CompletionCall call) {
            calls.add(call);
            Object next = answers.poll();
            if (next == null) {
                throw new AiUnavailableException("503 from ai-gateway", java.time.Duration.ofMinutes(20), false, null);
            }
            AiStamp stamp =
                    new AiStamp("call-" + calls.size(), call.promptKey(), 1, "GROQ", "openai/gpt-oss-120b", false);
            return next instanceof String text
                    ? new Completion(null, text, stamp, 100, 20)
                    : new Completion((Map<String, Object>) next, null, stamp, 100, 20);
        }

        @Override
        public List<float[]> embed(List<String> texts, @Nullable UUID userId) {
            if (embeddingsDown) {
                throw new AiUnavailableException("ollama down", null, false, null);
            }
            return texts.stream().map(t -> new float[] {t.length(), 1f}).toList();
        }
    }
}
