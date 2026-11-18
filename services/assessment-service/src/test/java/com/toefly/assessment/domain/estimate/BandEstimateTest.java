package com.toefly.assessment.domain.estimate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BandEstimateTest {

    private static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");
    private static final Instant LONG_AGO = NOW.minus(Duration.ofDays(30));

    private static List<Boolean> bas(int correct, int total) {
        List<Boolean> items = new ArrayList<>(Collections.nCopies(correct, true));
        items.addAll(Collections.nCopies(total - correct, false));
        return items;
    }

    @Test
    @DisplayName("AC-13.01.2 the scales follow band-scale-v1 (example A of docs/03 §8.7)")
    void followsScale() {
        assertThat(BandScale.ofTheta(1.30)).isCloseTo(4.63, within(0.01));
        assertThat(BandScale.ofTheta(0.80)).isCloseTo(4.24, within(0.01));
        assertThat(BandScale.ofShare(0.74)).isCloseTo(4.75, within(0.001));
        assertThat(BandScale.ofTheta(-9)).isEqualTo(1.0);
        assertThat(BandScale.ofShare(1.2)).isEqualTo(6.0);
        assertThat(Range80.of(4.18, 4.63, 5.11)).isEqualTo(new Range80(4.0, 5.0));
        assertThat(Range80.of(4.3, 4.6, 4.4)).isEqualTo(new Range80(4.5, 5.0));
        assertThat(Range80.of(4.3, 4.2, 4.4)).isEqualTo(new Range80(4.0, 4.5));
        assertThat(Range80.of(5.9, 6.0, 6.4)).isEqualTo(new Range80(5.5, 6.0));
        assertThat(BandScale.cefr(4.5)).isEqualTo("B2");
        assertThat(BandScale.cefr(6.0)).isEqualTo("C2");
        assertThat(BandScale.cefr(5.0)).isEqualTo("C1");
        assertThat(BandScale.cefr(3.0)).isEqualTo("B1");
        assertThat(BandScale.cefr(2.5)).isEqualTo("A2");
        assertThat(BandScale.cefr(1.5)).isEqualTo("A1");
        assertThat(BandScale.comparableLow(4.5)).isEqualTo(84);
        assertThat(BandScale.comparableHigh(4.5)).isEqualTo(94);
        assertThat(BandScale.comparableHigh(6.0)).isEqualTo(120);
    }

    @Test
    @DisplayName("AC-13.01.1 sections with enough data are READY, Speaking needs data until phase 6")
    void computesSections() {
        EstimateInputs inputs = new EstimateInputs(
                LONG_AGO,
                Map.of(
                        Section.READING, new EstimateInputs.Ability(1.30, 0.45, 40),
                        Section.LISTENING, new EstimateInputs.Ability(0.80, 0.40, 25)),
                bas(24, 30),
                List.of(3.5, 4.0),
                List.of(3.0, 3.5),
                NOW);
        BandEstimate estimate = BandEstimate.compute(inputs, NOW);
        SectionEstimate reading = estimate.section(Section.READING);
        assertThat(reading.ready()).isTrue();
        assertThat(reading.low()).isEqualTo(4.0);
        assertThat(reading.high()).isEqualTo(5.0);
        assertThat(reading.point()).isEqualTo(4.5);
        SectionEstimate writing = estimate.section(Section.WRITING);
        assertThat(writing.ready()).isTrue();
        assertThat(writing.n()).isEqualTo(4);
        assertThat(writing.continuous()).isBetween(4.5, 5.5);
        assertThat(estimate.section(Section.SPEAKING).ready()).isFalse();
        assertThat(estimate.overall()).isNull();
    }

    @Test
    @DisplayName("AC-13.01.3 under 7 days, under 20 items or under 2 works there is not enough data")
    void needsMoreData() {
        EstimateInputs fresh = new EstimateInputs(
                NOW.minus(Duration.ofDays(2)),
                Map.of(Section.READING, new EstimateInputs.Ability(1.0, 0.3, 50)),
                List.of(),
                List.of(4.0, 4.0),
                List.of(),
                NOW);
        BandEstimate early = BandEstimate.compute(fresh, NOW);
        assertThat(early.sections()).noneMatch(SectionEstimate::ready);
        EstimateInputs thin = new EstimateInputs(
                null,
                Map.of(Section.LISTENING, new EstimateInputs.Ability(1.0, 0.3, 12)),
                List.of(),
                List.of(4.0),
                List.of(),
                NOW);
        BandEstimate estimate = BandEstimate.compute(thin, NOW);
        assertThat(estimate.section(Section.LISTENING).remaining()).isEqualTo(8);
        assertThat(estimate.section(Section.WRITING).remaining()).isEqualTo(1);
        assertThat(estimate.section(Section.READING).remaining()).isEqualTo(20);
    }

    @Test
    @DisplayName("AC-13.01.3 a range wider than 2 bands is not shown")
    void hidesWideRanges() {
        EstimateInputs wide = new EstimateInputs(
                null,
                Map.of(Section.READING, new EstimateInputs.Ability(0.0, 2.5, 30)),
                List.of(),
                List.of(),
                List.of(),
                NOW);
        SectionEstimate reading = BandEstimate.compute(wide, NOW).section(Section.READING);
        assertThat(reading.ready()).isFalse();
        assertThat(reading.remaining()).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-11.05.6 Writing weighs recent first versions and scales without Build a Sentence")
    void weighsWriting() {
        assertThat(BandEstimate.weightedMean(List.of(4.0, 2.0))).isCloseTo(3.25, within(0.001));
        assertThat(BandEstimate.weightedShare(List.of(true, false))).isCloseTo(1 / 1.95, within(0.001));
        EstimateInputs onlyWriting = new EstimateInputs(null, Map.of(), List.of(), List.of(4.0), List.of(4.0), NOW);
        SectionEstimate writing = BandEstimate.compute(onlyWriting, NOW).section(Section.WRITING);
        assertThat(writing.continuous()).isCloseTo(BandScale.ofShare(0.8), within(0.001));
    }

    @Test
    @DisplayName("AC-13.01.1 the overall band averages the four sections when all are READY")
    void overallWhenAllReady() {
        SectionEstimate r = new SectionEstimate(Section.READING, true, 4.5, 4.63, 4.0, 5.0, 4.18, 5.11, 40, 0);
        SectionEstimate l = new SectionEstimate(Section.LISTENING, true, 4.0, 4.24, 4.0, 4.5, 3.91, 4.64, 25, 0);
        SectionEstimate w = new SectionEstimate(Section.WRITING, true, 5.0, 4.75, 4.5, 5.0, 4.39, 5.14, 8, 0);
        SectionEstimate s = new SectionEstimate(Section.SPEAKING, true, 4.5, 4.26, 4.0, 4.5, 3.86, 4.69, 5, 0);
        BandEstimate estimate = new BandEstimate(List.of(r, l, w, s), null, NOW);
        assertThat(estimate.sections()).hasSize(4);
        var overall = BandEstimate.overall(List.of(r, l, w, s));
        assertThat(overall).isEqualTo(new BandEstimate.Overall(4.5, 4.0, 5.0));
    }
}
