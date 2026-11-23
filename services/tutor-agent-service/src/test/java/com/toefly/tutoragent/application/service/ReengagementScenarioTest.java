package com.toefly.tutoragent.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.toefly.tutoragent.application.port.out.ReengagementPublisherPort;
import com.toefly.tutoragent.application.port.out.ReengagementRepository;
import com.toefly.tutoragent.domain.learner.ExplanationLanguage;
import com.toefly.tutoragent.domain.reengagement.ComeBackMessage;
import com.toefly.tutoragent.domain.reengagement.Situation;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReengagementScenarioTest {

    /** 2026-11-22 10:05 in Minsk (UTC+3). */
    static final Instant MORNING = Instant.parse("2026-11-22T07:05:00Z");

    static final ZoneId MINSK = ZoneId.of("Europe/Minsk");

    final UUID user = UUID.randomUUID();
    final List<ReengagementRepository.Candidate> candidates = new ArrayList<>();
    final Set<String> written = new HashSet<>();
    final List<ComeBackMessage> sent = new ArrayList<>();
    final List<Boolean> fallbacks = new ArrayList<>();
    final Fakes.Ai ai = new Fakes.Ai();

    ReengagementService service() {
        ReengagementRepository repository = new ReengagementRepository() {
            @Override
            public List<Candidate> candidates(Instant idleBefore, Instant rebalancedAfter, int limit) {
                return candidates;
            }

            @Override
            public boolean written(UUID userId, Situation situation, String key) {
                return written.contains(situation + key);
            }

            @Override
            public void save(
                    UUID id, UUID userId, Situation situation, String key, ComeBackMessage m, boolean f, Instant at) {
                written.add(situation + key);
            }
        };
        ReengagementPublisherPort events = (id, u, s, m, l, f, at) -> {
            sent.add(m);
            fallbacks.add(f);
        };
        LearnerModelService learner = new LearnerModelService(
                new Fakes.Models(),
                new Fakes.Mistakes(),
                new Fakes.Memory(),
                new Fakes.Publications(),
                new Fakes.LearnerEvents(),
                new Fakes.Results(),
                new Fakes.Practice(),
                new Fakes.Assessment(),
                Fakes.taxonomy(),
                Fakes.TX,
                Fakes.clock());
        return new ReengagementService(
                repository,
                events,
                learner,
                new Fakes.Results(),
                new ChatScenarioTest.Owners(),
                ai,
                Fakes.TX,
                Fakes.clock());
    }

    @Test
    @DisplayName("two idle days at 10:00 local give one AWAY_2_DAYS message per episode (docs/06 §5.10)")
    void awayTwoDays() {
        candidates.add(new ReengagementRepository.Candidate(
                user, MORNING.minus(Duration.ofDays(3)), "Europe/Minsk", null, 2, 40, null));
        ai.answer(Map.of(
                "title",
                "Ready for a short task?",
                "body",
                "Your week is 40% done. One task is next.",
                "ctaLabel",
                "Open today",
                "deepLink",
                "toefly://today"));
        ReengagementService service = service();
        assertThat(service.writeDue(MORNING)).isEqualTo(1);
        assertThat(service.writeDue(MORNING)).isZero();
        assertThat(sent.getFirst().body()).contains("40%");
        assertThat(fallbacks).containsExactly(false);
        assertThat(service.writeDue(MORNING.plus(Duration.ofHours(1)))).isZero();
    }

    @Test
    @DisplayName("an invalid text twice gives the template (pressure words, invented numbers)")
    void template() {
        candidates.add(new ReengagementRepository.Candidate(
                user, MORNING.minus(Duration.ofDays(9)), "Europe/Minsk", null, 2, 40, null));
        ai.answer(Map.of(
                "title", "You failed", "body", "You must study 99 minutes!!", "ctaLabel", "Go", "deepLink", "x"));
        ai.answer(Map.of(
                "title", "You failed", "body", "You must study 99 minutes!!", "ctaLabel", "Go", "deepLink", "x"));
        assertThat(service().writeDue(MORNING)).isEqualTo(1);
        assertThat(fallbacks).containsExactly(true);
        assertThat(sent.getFirst().title()).isEqualTo("Your plan is waiting for you");
    }

    @Test
    @DisplayName("situations: exam soon, a week behind, nothing for an active learner")
    void situations() {
        Instant now = MORNING;
        assertThat(Situation.of(now.minus(Duration.ofDays(3)), LocalDate.parse("2026-12-01"), null, 2, MINSK, now))
                .get()
                .extracting(Situation.Episode::situation)
                .isEqualTo(Situation.EXAM_SOON_INACTIVE);
        assertThat(Situation.of(now.minus(Duration.ofHours(5)), null, now.minus(Duration.ofHours(3)), 4, MINSK, now))
                .get()
                .extracting(Situation.Episode::key)
                .isEqualTo("week-4");
        assertThat(Situation.of(now.minus(Duration.ofHours(5)), null, null, 4, MINSK, now))
                .isEmpty();
        ComeBackMessage ok =
                ComeBackMessage.template(Situation.WEEK_BEHIND, "Academic Discussion", 12, ExplanationLanguage.RU);
        assertThat(ok.violations(Set.of("12"))).isEmpty();
        assertThat(ComeBackMessage.template(Situation.AWAY_2_DAYS, "", 0, ExplanationLanguage.EN)
                        .body())
                .startsWith("Start");
        assertThat(new ComeBackMessage("", "x".repeat(300), "c".repeat(30), "l").violations(Set.of()))
                .hasSize(3);
    }
}
