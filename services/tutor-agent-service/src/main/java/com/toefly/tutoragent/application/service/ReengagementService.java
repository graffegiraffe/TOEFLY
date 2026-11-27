package com.toefly.tutoragent.application.service;

import com.toefly.tutoragent.application.port.AiUnavailableException;
import com.toefly.tutoragent.application.port.in.LearnerModelUseCase;
import com.toefly.tutoragent.application.port.in.ReengagementUseCase;
import com.toefly.tutoragent.application.port.out.AiPort;
import com.toefly.tutoragent.application.port.out.OwnerToolsPort;
import com.toefly.tutoragent.application.port.out.PracticeResultRepository;
import com.toefly.tutoragent.application.port.out.ReengagementPublisherPort;
import com.toefly.tutoragent.application.port.out.ReengagementRepository;
import com.toefly.tutoragent.domain.learner.ExplanationLanguage;
import com.toefly.tutoragent.domain.reengagement.ComeBackMessage;
import com.toefly.tutoragent.domain.reengagement.Situation;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ReengagementWriter (docs/06 §5.10, docs/12 phase 5c decision 7): at 10:00 in the learner's time zone, one message
 * per situation and episode, worded by {@code tutor.reengagement} and validated, with a template fallback.
 */
public class ReengagementService implements ReengagementUseCase {

    static final String PROMPT = "tutor.reengagement";
    static final LocalTime MORNING = LocalTime.of(10, 0);
    static final Duration WINDOW = Duration.ofMinutes(15);

    private static final Logger log = LoggerFactory.getLogger(ReengagementService.class);

    private final ReengagementRepository repository;
    private final ReengagementPublisherPort events;
    private final LearnerModelUseCase learner;
    private final PracticeResultRepository results;
    private final OwnerToolsPort owners;
    private final AiPort ai;
    private final Transactions transactions;
    private final Clock clock;

    public ReengagementService(
            ReengagementRepository repository,
            ReengagementPublisherPort events,
            LearnerModelUseCase learner,
            PracticeResultRepository results,
            OwnerToolsPort owners,
            AiPort ai,
            Transactions transactions,
            Clock clock) {
        this.repository = repository;
        this.events = events;
        this.learner = learner;
        this.results = results;
        this.owners = owners;
        this.ai = ai;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public int writeDue(Instant now) {
        int written = 0;
        for (ReengagementRepository.Candidate c :
                repository.candidates(now.minus(Duration.ofDays(2)), now.minus(Duration.ofDays(1)), 500)) {
            ZoneId zone = zone(c.timezone());
            LocalTime local = LocalTime.ofInstant(now, zone);
            if (local.isBefore(MORNING) || !local.isBefore(MORNING.plus(WINDOW))) {
                continue;
            }
            Optional<Situation.Episode> episode =
                    Situation.of(c.lastActiveAt(), c.examDate(), c.rebalancedAt(), c.weekNo(), zone, now);
            if (episode.isEmpty()
                    || repository.written(
                            c.userId(), episode.get().situation(), episode.get().key())) {
                continue;
            }
            write(c, episode.get(), zone, now);
            written++;
        }
        return written;
    }

    void write(ReengagementRepository.Candidate c, Situation.Episode episode, ZoneId zone, Instant now) {
        ExplanationLanguage language = learner.get(c.userId()).language();
        Map<String, Object> data = data(c, zone, now);
        String nextTask = String.valueOf(data.getOrDefault("nextTaskTitle", ""));
        int nextMinutes = data.get("nextTaskMinutes") instanceof Number n ? n.intValue() : 10;
        Set<String> numbers = numbers(data);
        ComeBackMessage message = null;
        List<String> violations = List.of();
        for (int round = 0; round < 2 && message == null; round++) {
            try {
                AiPort.Completion completion = ai.complete(new AiPort.CompletionCall(
                        "REENGAGEMENT",
                        PROMPT,
                        null,
                        Map.of(
                                "situation",
                                episode.situation().name(),
                                "data",
                                data.toString(),
                                "language",
                                language == ExplanationLanguage.RU ? "Russian" : "English",
                                "violations",
                                violations.isEmpty() ? "" : "Fix these problems: " + String.join("; ", violations)),
                        c.userId(),
                        "reengagement",
                        "BACKGROUND",
                        60_000,
                        "reengagement:" + c.userId() + ":" + episode.situation() + ":" + episode.key() + ":" + round,
                        null));
                Map<String, Object> out = completion.output();
                if (out == null) {
                    violations = List.of("answer with the JSON object of the schema");
                    continue;
                }
                ComeBackMessage candidate = new ComeBackMessage(
                        String.valueOf(out.getOrDefault("title", "")),
                        String.valueOf(out.getOrDefault("body", "")),
                        String.valueOf(out.getOrDefault("ctaLabel", "")),
                        "toefly://today");
                violations = candidate.violations(numbers);
                if (violations.isEmpty()) {
                    message = candidate;
                }
            } catch (AiUnavailableException unavailable) {
                log.info("Come-back message for {} uses the template: {}", c.userId(), unavailable.getMessage());
                break;
            }
        }
        boolean fallback = message == null;
        ComeBackMessage chosen =
                fallback ? ComeBackMessage.template(episode.situation(), nextTask, nextMinutes, language) : message;
        UUID id = UUID.randomUUID();
        transactions.run(() -> {
            repository.save(id, c.userId(), episode.situation(), episode.key(), chosen, fallback, now);
            events.messageCreated(id, c.userId(), episode.situation(), chosen, language, fallback, now);
        });
    }

    private Map<String, Object> data(ReengagementRepository.Candidate c, ZoneId zone, Instant now) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("firstName", "[PERSON_1]");
        data.put("weekProgressPct", c.progressPercent());
        if (c.examDate() != null) {
            data.put("examInDays", ChronoUnit.DAYS.between(LocalDate.ofInstant(now, zone), c.examDate()));
        }
        try {
            Map<String, Object> week = owners.currentWeek(c.userId(), null);
            Object today = week.get("today");
            if (today instanceof List<?> tasks && !tasks.isEmpty() && tasks.getFirst() instanceof Map<?, ?> task) {
                data.put("nextTaskTitle", String.valueOf(task.get("title")));
                Object minutes = task.get("estimatedMinutes");
                data.put("nextTaskMinutes", minutes instanceof Number n ? n.intValue() : 10);
            }
        } catch (RuntimeException unavailable) {
            log.debug("No current week for {}: {}", c.userId(), unavailable.getMessage());
        }
        results.between(c.userId(), now.minus(Duration.ofDays(28)), now).stream()
                .filter(r -> r.score() != null)
                .max(Comparator.comparing(PracticeResultRepository.Result::occurredAt))
                .ifPresent(r -> data.put(
                        "lastWin",
                        "Your last " + r.taskType().toLowerCase().replace('_', ' ') + " scored " + r.score()));
        return data;
    }

    private static Set<String> numbers(Map<String, Object> data) {
        Set<String> numbers = new HashSet<>();
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("\\d+(\\.\\d+)?").matcher(data.toString());
        while (m.find()) {
            numbers.add(m.group());
        }
        return numbers;
    }

    private static ZoneId zone(@Nullable String timezone) {
        try {
            return timezone == null ? ZoneOffset.UTC : ZoneId.of(timezone);
        } catch (DateTimeException unknown) {
            return ZoneOffset.UTC;
        }
    }
}
