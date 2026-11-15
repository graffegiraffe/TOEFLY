package com.toefly.aigateway.application.service;

import com.toefly.aigateway.application.port.in.SpeechUseCase;
import com.toefly.aigateway.application.port.out.AudioSourcePort;
import com.toefly.aigateway.application.port.out.ProviderFailure;
import com.toefly.aigateway.application.port.out.ResponseCachePort;
import com.toefly.aigateway.application.port.out.ResponseCachePort.CachedAnswer;
import com.toefly.aigateway.application.port.out.RoutingTablePort;
import com.toefly.aigateway.application.port.out.SpeechPort;
import com.toefly.aigateway.application.port.out.TranscriptionPort;
import com.toefly.aigateway.domain.call.AiCall;
import com.toefly.aigateway.domain.call.AiCallOutcome;
import com.toefly.aigateway.domain.call.TokenUsage;
import com.toefly.aigateway.domain.prompt.PromptRuleException;
import com.toefly.aigateway.domain.quota.Priority;
import com.toefly.aigateway.domain.quota.QuotaDemand;
import com.toefly.aigateway.domain.routing.AiProvider;
import com.toefly.aigateway.domain.routing.AiUseCase;
import com.toefly.aigateway.domain.routing.ModelRef;
import com.toefly.aigateway.domain.routing.ModelRoute;
import com.toefly.aigateway.domain.speech.Assessment;
import com.toefly.aigateway.domain.speech.Transcript;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Audio calls (§2.2.3): Whisper on Groq, Azure pronunciation assessment and neural TTS. Audio cannot be masked; the
 * learner's consent covers it (§3.2). TTS answers are cached by {@code sha256(ssml + voice)}.
 */
@Service
class SpeechService implements SpeechUseCase {

    /** 16 kHz, 16-bit mono PCM: the format speech-service normalises recordings to. */
    static final double WAV_BYTES_PER_SECOND = 32_000;

    private final RoutingTablePort routing;
    private final AudioSourcePort audio;
    private final TranscriptionPort transcription;
    private final SpeechPort speech;
    private final FallbackExecutor fallback;
    private final ResponseCachePort cache;
    private final CallJournal journal;
    private final Clock clock;

    SpeechService(
            RoutingTablePort routing,
            AudioSourcePort audio,
            TranscriptionPort transcription,
            SpeechPort speech,
            FallbackExecutor fallback,
            ResponseCachePort cache,
            CallJournal journal,
            Clock clock) {
        this.routing = routing;
        this.audio = audio;
        this.transcription = transcription;
        this.speech = speech;
        this.fallback = fallback;
        this.cache = cache;
        this.journal = journal;
        this.clock = clock;
    }

    @Override
    public Transcribed transcribe(TranscribeCommand command) {
        Instant started = clock.instant();
        String callId = journal.nextId();
        byte[] recording = fetch(command.audioUrl());
        double seconds = recording.length / WAV_BYTES_PER_SECOND;
        ModelRoute route = route(AiUseCase.TRANSCRIPTION);
        List<ModelRef> candidates = route.candidates().stream()
                .filter(model -> model.provider() == AiProvider.GROQ)
                .toList();
        FallbackExecutor.Plan plan = plan(
                AiUseCase.TRANSCRIPTION,
                candidates,
                command.userId(),
                started,
                QuotaDemand.audio(seconds),
                route.params().timeout());
        FallbackExecutor.Answer<Transcript> answer;
        try {
            answer = fallback.run(
                    plan,
                    (model, previous) -> transcription.transcribe(
                            model, recording, fileName(command.audioUrl()), command.language(), command.prompt()),
                    t -> new TokenUsage(0, 0, t.durationSec(), 0));
        } catch (FallbackExecutor.Exhausted exhausted) {
            record(
                    callId,
                    command.callerService(),
                    AiUseCase.TRANSCRIPTION,
                    command.feature(),
                    command.userId(),
                    exhausted.lastModel(),
                    0,
                    false,
                    TokenUsage.NONE,
                    started,
                    exhausted.outcome());
            throw exhausted.problem();
        }
        AiCall call = record(
                callId,
                command.callerService(),
                AiUseCase.TRANSCRIPTION,
                command.feature(),
                command.userId(),
                answer.model(),
                answer.depth(),
                false,
                new TokenUsage(0, 0, answer.value().durationSec(), 0),
                started,
                AiCallOutcome.SUCCESS);
        return new Transcribed(answer.value(), call.stamp());
    }

    @Override
    public Assessed assess(AssessCommand command) {
        if (command.request().scripted()
                && (command.request().referenceText() == null
                        || command.request().referenceText().isBlank())) {
            throw new PromptRuleException("validation-failed", "SCRIPTED assessment needs referenceText");
        }
        Instant started = clock.instant();
        String callId = journal.nextId();
        byte[] recording = fetch(command.audioUrl());
        double seconds = recording.length / WAV_BYTES_PER_SECOND;
        ModelRoute route = route(AiUseCase.PRONUNCIATION);
        FallbackExecutor.Plan plan = plan(
                AiUseCase.PRONUNCIATION,
                route.candidates(),
                command.userId(),
                started,
                QuotaDemand.audio(seconds),
                route.params().timeout());
        FallbackExecutor.Answer<Assessment> answer;
        try {
            answer = fallback.run(
                    plan,
                    (model, previous) -> speech.assess(recording, command.request()),
                    a -> new TokenUsage(0, 0, a.durationSec() > 0 ? a.durationSec() : seconds, 0));
        } catch (FallbackExecutor.Exhausted exhausted) {
            record(
                    callId,
                    command.callerService(),
                    AiUseCase.PRONUNCIATION,
                    command.feature(),
                    command.userId(),
                    exhausted.lastModel(),
                    0,
                    false,
                    TokenUsage.NONE,
                    started,
                    exhausted.outcome());
            throw exhausted.problem();
        }
        double used = answer.value().durationSec() > 0 ? answer.value().durationSec() : seconds;
        AiCall call = record(
                callId,
                command.callerService(),
                AiUseCase.PRONUNCIATION,
                command.feature(),
                command.userId(),
                answer.model(),
                answer.depth(),
                false,
                new TokenUsage(0, 0, used, 0),
                started,
                AiCallOutcome.SUCCESS);
        return new Assessed(answer.value(), call.stamp());
    }

    @Override
    public Synthesized synthesize(SynthesizeCommand command) {
        String ssml = command.ssml() != null && !command.ssml().isBlank()
                ? command.ssml()
                : ssml(requireText(command.text()), command.voice());
        int characters = ssml.length();
        Instant started = clock.instant();
        String callId = journal.nextId();
        ModelRoute route = route(AiUseCase.TTS);
        String key = GenerationService.sha256("TTS|" + command.voice() + "|" + command.format() + "|" + ssml);
        Optional<CachedAnswer> hit = cache.get(key);
        if (hit.isPresent()) {
            ModelRef model = new ModelRef(
                    AiProvider.valueOf(hit.get().provider()), hit.get().model());
            AiCall call = journal.record(
                    callId,
                    entry(
                            command.callerService(),
                            AiUseCase.TTS,
                            null,
                            command.userId(),
                            model,
                            0,
                            false,
                            true,
                            TokenUsage.NONE,
                            started,
                            AiCallOutcome.CACHE_HIT));
            return new Synthesized(Base64.getDecoder().decode(hit.get().content()), characters, call.stamp());
        }
        List<ModelRef> candidates = route.candidates().stream()
                .filter(model -> model.provider() == AiProvider.AZURE_SPEECH)
                .toList();
        FallbackExecutor.Plan plan = plan(
                AiUseCase.TTS,
                candidates,
                command.userId(),
                started,
                QuotaDemand.characters(characters),
                route.params().timeout());
        FallbackExecutor.Answer<byte[]> answer;
        try {
            answer = fallback.run(
                    plan,
                    (model, previous) -> speech.synthesize(ssml, command.format()),
                    bytes -> new TokenUsage(0, 0, 0, characters));
        } catch (FallbackExecutor.Exhausted exhausted) {
            record(
                    callId,
                    command.callerService(),
                    AiUseCase.TTS,
                    null,
                    command.userId(),
                    exhausted.lastModel(),
                    0,
                    false,
                    TokenUsage.NONE,
                    started,
                    exhausted.outcome());
            throw exhausted.problem();
        }
        AiCall call = record(
                callId,
                command.callerService(),
                AiUseCase.TTS,
                null,
                command.userId(),
                answer.model(),
                answer.depth(),
                false,
                new TokenUsage(0, 0, 0, characters),
                started,
                AiCallOutcome.SUCCESS);
        cache.put(
                key,
                new CachedAnswer(
                        Base64.getEncoder().encodeToString(answer.value()),
                        answer.model().provider().name(),
                        answer.model().model(),
                        "tts",
                        1));
        return new Synthesized(answer.value(), characters, call.stamp());
    }

    static String ssml(String text, String voice) {
        String locale = voice.length() >= 5 ? voice.substring(0, 5) : "en-US";
        String escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return "<speak version=\"1.0\" xmlns=\"http://www.w3.org/2001/10/synthesis\" xml:lang=\"" + locale
                + "\"><voice name=\"" + voice + "\">" + escaped + "</voice></speak>";
    }

    private static String requireText(@Nullable String text) {
        if (text == null || text.isBlank()) {
            throw new PromptRuleException("validation-failed", "send ssml or text");
        }
        return text;
    }

    private ModelRoute route(AiUseCase useCase) {
        return routing.current()
                .route(useCase, null)
                .orElseThrow(() -> new PromptRuleException("validation-failed", "no route for " + useCase));
    }

    private FallbackExecutor.Plan plan(
            AiUseCase useCase,
            List<ModelRef> candidates,
            @Nullable UUID userId,
            Instant started,
            QuotaDemand demand,
            Duration timeout) {
        if (candidates.isEmpty()) {
            throw new PromptRuleException("validation-failed", "the " + useCase + " route has no usable candidate");
        }
        return new FallbackExecutor.Plan(
                useCase,
                candidates,
                Priority.INTERACTIVE,
                userId,
                started.plus(Priority.INTERACTIVE.waitPerCandidate().multipliedBy(candidates.size())),
                model -> demand,
                model -> timeout);
    }

    /** A missing or unreadable recording is the caller's problem; an unreachable store is a 503. */
    private byte[] fetch(String url) {
        try {
            return audio.fetch(url);
        } catch (ProviderFailure failure) {
            if (failure.kind() == ProviderFailure.Kind.REJECTED) {
                throw new PromptRuleException("validation-failed", failure.getMessage());
            }
            throw new com.toefly.aigateway.application.port.in.AiProviderUnavailableException(
                    "The recording cannot be read right now.", FallbackExecutor.DEFAULT_RETRY_AFTER.toSeconds());
        }
    }

    private static String fileName(String url) {
        String path = url.contains("?") ? url.substring(0, url.indexOf('?')) : url;
        String name = path.substring(path.lastIndexOf('/') + 1);
        return name.isBlank() ? "recording.wav" : name;
    }

    private AiCall record(
            String callId,
            String caller,
            AiUseCase useCase,
            @Nullable String feature,
            @Nullable UUID userId,
            ModelRef model,
            int depth,
            boolean cacheHit,
            TokenUsage usage,
            Instant started,
            AiCallOutcome outcome) {
        return journal.record(
                callId,
                entry(caller, useCase, feature, userId, model, depth, false, cacheHit, usage, started, outcome));
    }

    private CallJournal.Entry entry(
            String caller,
            AiUseCase useCase,
            @Nullable String feature,
            @Nullable UUID userId,
            ModelRef model,
            int depth,
            boolean degraded,
            boolean cacheHit,
            TokenUsage usage,
            Instant started,
            AiCallOutcome outcome) {
        return new CallJournal.Entry(
                caller,
                useCase,
                feature,
                Priority.INTERACTIVE,
                userId,
                null,
                null,
                model,
                depth,
                degraded,
                cacheHit,
                null,
                null,
                usage,
                Duration.between(started, clock.instant()).toMillis(),
                null,
                outcome,
                outcome == AiCallOutcome.SUCCESS || outcome == AiCallOutcome.CACHE_HIT
                        ? null
                        : GenerationService.slug(outcome),
                null);
    }
}
