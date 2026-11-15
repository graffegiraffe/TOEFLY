package com.toefly.aigateway.application.port.in;

import com.toefly.aigateway.domain.call.AiStamp;
import com.toefly.aigateway.domain.speech.Assessment;
import com.toefly.aigateway.domain.speech.AssessmentRequest;
import com.toefly.aigateway.domain.speech.Transcript;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Transcription, pronunciation assessment and synthesis (§2.2.3). */
public interface SpeechUseCase {

    Transcribed transcribe(TranscribeCommand command);

    Assessed assess(AssessCommand command);

    Synthesized synthesize(SynthesizeCommand command);

    record TranscribeCommand(
            String callerService,
            String audioUrl,
            String language,
            @Nullable String prompt,
            @Nullable UUID userId,
            @Nullable String feature) {}

    record Transcribed(Transcript transcript, AiStamp aiStamp) {}

    record AssessCommand(
            String callerService,
            String audioUrl,
            AssessmentRequest request,
            @Nullable UUID userId,
            @Nullable String feature) {}

    record Assessed(Assessment assessment, AiStamp aiStamp) {}

    record SynthesizeCommand(
            String callerService,
            @Nullable String ssml,
            @Nullable String text,
            String voice,
            String format,
            @Nullable UUID userId) {}

    record Synthesized(byte[] audio, int characters, AiStamp aiStamp) {}
}
