package com.toefly.aigateway.application.port.out;

import com.toefly.aigateway.domain.routing.ModelRef;
import com.toefly.aigateway.domain.speech.Transcript;
import org.jspecify.annotations.Nullable;

/** Speech to text with word timestamps (Groq Whisper); throws {@link ProviderFailure}. */
public interface TranscriptionPort {

    Transcript transcribe(ModelRef model, byte[] audio, String fileName, String language, @Nullable String prompt);
}
