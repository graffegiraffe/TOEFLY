package com.toefly.practice.domain.attempt;

import static com.toefly.practice.domain.attempt.Attempts.T0;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Property: for any sequence of commands, the state rebuilt from the written events equals the state the commands
 * produced, and so does a snapshot plus the tail (ADR-0010, phase 3 DoD). 500 random sequences, fixed seeds.
 */
class AttemptReplayPropertyTest {

    static Stream<Long> seeds() {
        return LongStream.range(0, 500).boxed();
    }

    @ParameterizedTest(name = "seed {0}")
    @MethodSource("seeds")
    @DisplayName("ADR-0010: replaying the stream gives the state the commands produced")
    void replayEqualsLiveState(long seed) {
        Random random = new Random(seed);
        AttemptMode mode = random.nextInt(4) == 0 ? AttemptMode.EXAM : AttemptMode.PRACTICE;
        Attempt live = Attempt.start(
                Attempts.started(mode, 1 + random.nextInt(10), mode == AttemptMode.EXAM ? T0.plusSeconds(900) : null));
        List<AttemptEvent> written = new ArrayList<>(live.pendingEvents());
        live.markPersisted();

        Instant now = T0;
        int commands = random.nextInt(40);
        for (int i = 0; i < commands; i++) {
            now = now.plusSeconds(1 + random.nextInt(60));
            try {
                runRandomCommand(live, random, now);
            } catch (RuntimeException rejected) {
                // a rejected command must not leave events behind
                assertThat(rejected).isNotInstanceOf(NullPointerException.class);
            }
            written.addAll(live.pendingEvents());
            live.markPersisted();
        }

        Attempt replayed = Attempt.replay(written);
        assertThat(replayed.snapshot()).isEqualTo(live.snapshot());
        assertThat(replayed.version()).isEqualTo(written.size());

        int cut = 1 + random.nextInt(written.size());
        Attempt prefix = Attempt.replay(written.subList(0, cut));
        Attempt restored = Attempt.restore(prefix.snapshot(), written.subList(cut, written.size()));
        assertThat(restored.snapshot()).isEqualTo(live.snapshot());
    }

    private static void runRandomCommand(Attempt attempt, Random random, Instant now) {
        List<PinnedItem> items = attempt.startedEvent().items();
        UUID item = random.nextInt(10) == 0
                ? UUID.randomUUID()
                : items.get(random.nextInt(items.size())).itemId();
        switch (random.nextInt(12)) {
            case 0, 1, 2, 3 -> attempt.recordAnswer(item, randomAnswer(random), now, now.minusMillis(300), 1500L);
            case 4 -> attempt.clearAnswer(item, now);
            case 5 -> attempt.pause(now);
            case 6 -> attempt.resume(now);
            case 7 -> attempt.submit(now, random.nextBoolean() ? SubmitReason.USER : SubmitReason.TIMEOUT, 90);
            case 8, 9 -> attempt.score(randomOutcomes(attempt, random), now);
            case 10 -> attempt.abandon(now, AbandonReason.USER_EXIT);
            default -> attempt.recordAnswer(item, new Answer.Text(""), now, null, null);
        }
    }

    private static Answer randomAnswer(Random random) {
        return switch (random.nextInt(4)) {
            case 0 -> new Answer.Choice(List.of(String.valueOf((char) ('A' + random.nextInt(4)))));
            case 1 -> new Answer.Text("ab" + random.nextInt(3));
            case 2 -> new Answer.Sequence(Arrays.asList("c1", random.nextBoolean() ? null : "c2"));
            default -> new Answer.Mapping(List.of(new Answer.Mapping.Pair("B", "went")));
        };
    }

    private static List<ItemOutcome> randomOutcomes(Attempt attempt, Random random) {
        return attempt.startedEvent().items().stream()
                .filter(item -> random.nextBoolean())
                .map(item -> {
                    boolean correct = random.nextBoolean();
                    return new ItemOutcome(
                            item.itemId(), item.ordinal(), correct ? 1 : 0, 1, true, correct, false, item.b());
                })
                .toList();
    }
}
