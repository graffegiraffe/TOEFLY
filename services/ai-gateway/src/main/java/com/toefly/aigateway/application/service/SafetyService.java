package com.toefly.aigateway.application.service;

import com.toefly.aigateway.application.port.in.AiProviderUnavailableException;
import com.toefly.aigateway.application.port.out.GuardModelPort;
import com.toefly.aigateway.application.port.out.NerPort;
import com.toefly.aigateway.application.port.out.ProviderFailure;
import com.toefly.aigateway.domain.safety.DictionaryPiiDetector;
import com.toefly.aigateway.domain.safety.GuardAssessment;
import com.toefly.aigateway.domain.safety.GuardModelVerdict;
import com.toefly.aigateway.domain.safety.GuardPolicy;
import com.toefly.aigateway.domain.safety.HeuristicHit;
import com.toefly.aigateway.domain.safety.InjectionHeuristics;
import com.toefly.aigateway.domain.safety.MaskingSession;
import com.toefly.aigateway.domain.safety.PiiMasker;
import com.toefly.aigateway.domain.safety.PiiSpan;
import com.toefly.aigateway.domain.safety.RegexPiiDetector;
import com.toefly.aigateway.domain.safety.TextSanitizer;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** The local safety layer of §3: masking before any external call, heuristics and the guard model. */
@Component
class SafetyService {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(SafetyService.class);

    static final long LOCAL_MODELS_RETRY_SECONDS = 30;
    private static final int GUARD_ATTEMPTS = 2;

    private final NerPort ner;
    private final GuardModelPort guard;
    private final GatewaySettings settings;
    private final MeterRegistry meters;

    SafetyService(NerPort ner, GuardModelPort guard, GatewaySettings settings, MeterRegistry meters) {
        this.ner = ner;
        this.guard = guard;
        this.settings = settings;
        this.meters = meters;
    }

    /** Masks a text with regex, dictionary and NER stages; fails closed when the NER model is down. */
    String mask(String text, MaskingSession session, List<String> hints, Set<String> allowList) {
        String clean = TextSanitizer.stripInvisible(text);
        if (clean.isBlank()) {
            return clean;
        }
        List<PiiSpan> spans = new ArrayList<>(RegexPiiDetector.detect(clean));
        spans.addAll(DictionaryPiiDetector.detect(clean, hints));
        if (settings.nerEnabled() && clean.codePoints().anyMatch(Character::isLetter)) {
            try {
                spans.addAll(ner.find(clean));
            } catch (ProviderFailure failure) {
                LOG.warn("Masking model unavailable, the call is refused: {}", failure.getMessage(), failure);
                meters.counter("toefly.ai.safety.unavailable", "stage", "ner").increment();
                throw new AiProviderUnavailableException(
                        "The local masking model is unavailable, so no text may leave.", LOCAL_MODELS_RETRY_SECONDS);
            }
        }
        String masked = PiiMasker.mask(clean, spans, session, allowList);
        meters.counter("toefly.ai.pii.masked").increment(spans.isEmpty() ? 0 : 1);
        return masked;
    }

    /**
     * Heuristics on input, then the guard model where the policy asks for it. CHAT fails closed after two failed
     * calls; other policies go on with {@code modelAvailable = false} (§3.4).
     */
    GuardAssessment check(String text, GuardPolicy policy, boolean output) {
        long started = System.nanoTime();
        List<HeuristicHit> hits = output ? List.of() : InjectionHeuristics.scan(text);
        boolean modelWanted = output ? policy.checksOutput() : policy.checksInput();
        if (!modelWanted) {
            return GuardAssessment.combine(hits, null, elapsedMs(started), true);
        }
        @Nullable GuardModelVerdict verdict = null;
        for (int attempt = 1; attempt <= GUARD_ATTEMPTS && verdict == null; attempt++) {
            try {
                verdict = guard.classify(TextSanitizer.stripInvisible(text), output);
            } catch (ProviderFailure failure) {
                LOG.warn("Guard model unavailable (attempt {}): {}", attempt, failure.getMessage());
                meters.counter("toefly.ai.safety.unavailable", "stage", "guard").increment();
            }
        }
        if (verdict == null && policy.failsClosed()) {
            throw new AiProviderUnavailableException(
                    "The guard model is unavailable; the tutor answers with the unavailable template.",
                    LOCAL_MODELS_RETRY_SECONDS);
        }
        GuardAssessment assessment = GuardAssessment.combine(hits, verdict, elapsedMs(started), verdict != null);
        meters.counter(
                        "toefly.ai.guard",
                        "policy",
                        policy.name(),
                        "verdict",
                        assessment.verdict().name())
                .increment();
        return assessment;
    }

    /** Refusal template of a blocked turn (§3.6). */
    static String refusalCode(GuardAssessment assessment) {
        if (assessment.selfHarm()) {
            return "SELF_HARM";
        }
        return switch (assessment.verdict()) {
            case INJECTION -> "INJECTION";
            case DISHONESTY -> "SOLVE_BEFORE_SUBMIT";
            case UNSAFE -> "UNSAFE";
            case SAFE -> "NONE";
        };
    }

    private static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }
}
