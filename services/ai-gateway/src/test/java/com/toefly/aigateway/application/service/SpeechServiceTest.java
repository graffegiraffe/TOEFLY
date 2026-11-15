package com.toefly.aigateway.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.toefly.aigateway.application.port.in.AiProviderUnavailableException;
import com.toefly.aigateway.application.port.in.SpeechUseCase;
import com.toefly.aigateway.application.port.out.ProviderFailure;
import com.toefly.aigateway.application.port.out.SpeechPort;
import com.toefly.aigateway.application.port.out.TranscriptionPort;
import com.toefly.aigateway.domain.call.AiCallOutcome;
import com.toefly.aigateway.domain.prompt.PromptRuleException;
import com.toefly.aigateway.domain.routing.AiUseCase;
import com.toefly.aigateway.domain.routing.ModelRef;
import com.toefly.aigateway.domain.routing.ModelRoute;
import com.toefly.aigateway.domain.routing.RoutingTable;
import com.toefly.aigateway.domain.speech.AssessedWord;
import com.toefly.aigateway.domain.speech.Assessment;
import com.toefly.aigateway.domain.speech.AssessmentRequest;
import com.toefly.aigateway.domain.speech.Phoneme;
import com.toefly.aigateway.domain.speech.TranscribedWord;
import com.toefly.aigateway.domain.speech.Transcript;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpeechServiceTest {

    static final ModelRef TURBO = ModelRef.parse("GROQ:whisper-large-v3-turbo");
    static final ModelRef WHISPER = ModelRef.parse("GROQ:whisper-large-v3");
    static final ModelRef PA = ModelRef.parse("AZURE_SPEECH:pronunciation-assessment");
    static final ModelRef TTS = ModelRef.parse("AZURE_SPEECH:neural-tts");

    final Gateway gateway = new Gateway();
    final List<ModelRef> transcribedWith = new ArrayList<>();
    final List<String> synthesized = new ArrayList<>();
    boolean audioMissing;

    final TranscriptionPort whisper = (model, audio, name, language, prompt) -> {
        transcribedWith.add(model);
        if (model.equals(TURBO)) {
            throw ProviderFailure.unavailable("503", null);
        }
        return new Transcript("Yes, I agree.", List.of(new TranscribedWord("Yes", 0.42, 0.61, 0.98)), 44.7);
    };
    final SpeechPort azure = new SpeechPort() {
        @Override
        public Assessment assess(byte[] wav, AssessmentRequest request) {
            return new Assessment(
                    86,
                    92,
                    100.0,
                    78.0,
                    87.4,
                    "Study tables.",
                    2.0,
                    List.of(new AssessedWord(
                            "tables", 71, "Mispronunciation", 620, 410, List.of(new Phoneme("ey", 48)))));
        }

        @Override
        public byte[] synthesize(String ssml, String format) {
            synthesized.add(ssml);
            return new byte[] {1, 2, 3};
        }
    };
    final SpeechService speech;

    SpeechServiceTest() {
        gateway.routes = new RoutingTable(List.of(
                new ModelRoute(AiUseCase.TRANSCRIPTION, null, List.of(TURBO, WHISPER), Gateway.PARAMS),
                new ModelRoute(AiUseCase.PRONUNCIATION, null, List.of(PA), Gateway.PARAMS),
                new ModelRoute(AiUseCase.TTS, null, List.of(TTS), Gateway.PARAMS)));
        speech = new SpeechService(
                () -> gateway.routes,
                url -> {
                    if (audioMissing) {
                        throw new ProviderFailure(ProviderFailure.Kind.REJECTED, "404", null, null);
                    }
                    if (url.contains("down")) {
                        throw ProviderFailure.unavailable("store down", null);
                    }
                    return new byte[64_000];
                },
                whisper,
                azure,
                gateway.fallback,
                gateway.cache,
                gateway.journal,
                gateway.clock);
    }

    @Test
    void transcriptionFallsBackToTheSecondWhisperAndAccountsTheAudio() {
        var result = speech.transcribe(new SpeechUseCase.TranscribeCommand(
                "speech-service", "https://r2/rec.wav?sig=1", "en", "Interview", null, "AI_SPEAKING_CHECK"));

        assertThat(result.transcript().words()).hasSize(1);
        assertThat(result.aiStamp().fallbackDepth()).isEqualTo(1);
        assertThat(transcribedWith).containsExactly(TURBO, TURBO, WHISPER);
        assertThat(gateway.calls.saved.getLast().usage().audioSeconds()).isEqualTo(44.7);
    }

    @Test
    void scriptedAssessmentNeedsAReferenceAndIsAccountedInAudioSeconds() {
        assertThatThrownBy(() -> speech.assess(new SpeechUseCase.AssessCommand(
                        "speech-service",
                        "https://r2/a.wav",
                        new AssessmentRequest(true, null, "en-US", true, true, "Phoneme"),
                        null,
                        null)))
                .isInstanceOf(PromptRuleException.class);

        var result = speech.assess(new SpeechUseCase.AssessCommand(
                "speech-service",
                "https://r2/a.wav",
                new AssessmentRequest(true, "Study tables.", "en-US", true, true, "Phoneme"),
                null,
                null));

        assertThat(result.assessment().pronScore()).isEqualTo(87.4);
        assertThat(result.assessment().words().getFirst().phonemes().getFirst().accuracy())
                .isEqualTo(48);
        assertThat(gateway.calls.saved.getLast().usage().audioSeconds()).isEqualTo(2.0);
    }

    @Test
    void synthesisOfPlainTextIsWrappedInSsmlAndCached() {
        var command = new SpeechUseCase.SynthesizeCommand(
                "content-factory",
                null,
                "Fish & chips <3",
                "en-GB-RyanNeural",
                "audio-24khz-48kbitrate-mono-mp3",
                null);

        var first = speech.synthesize(command);
        var second = speech.synthesize(command);

        assertThat(synthesized).hasSize(1);
        assertThat(synthesized.getFirst()).contains("xml:lang=\"en-GB\"", "Fish &amp; chips &lt;3", "en-GB-RyanNeural");
        assertThat(second.audio()).isEqualTo(first.audio());
        assertThat(second.aiStamp().cacheHit()).isTrue();
        assertThat(gateway.calls.saved.getLast().outcome()).isEqualTo(AiCallOutcome.CACHE_HIT);
    }

    @Test
    void unreadableRecordingIsTheCallersProblemAndADownStoreIs503() {
        assertThatThrownBy(() -> speech.transcribe(new SpeechUseCase.TranscribeCommand(
                        "speech-service", "https://r2/down.wav", "en", null, null, null)))
                .isInstanceOf(AiProviderUnavailableException.class);
        audioMissing = true;
        assertThatThrownBy(() -> speech.transcribe(new SpeechUseCase.TranscribeCommand(
                        "speech-service", "https://r2/x.wav", "en", null, null, null)))
                .isInstanceOf(PromptRuleException.class);
        assertThatThrownBy(() -> speech.synthesize(new SpeechUseCase.SynthesizeCommand(
                        "speech-service", null, " ", "en-US-JennyNeural", "mp3", null)))
                .isInstanceOf(PromptRuleException.class);
    }
}
