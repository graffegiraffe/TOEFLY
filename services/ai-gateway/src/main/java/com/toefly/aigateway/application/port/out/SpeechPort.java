package com.toefly.aigateway.application.port.out;

import com.toefly.aigateway.domain.speech.Assessment;
import com.toefly.aigateway.domain.speech.AssessmentRequest;

/** Azure AI Speech: pronunciation assessment and neural TTS; throws {@link ProviderFailure}. */
public interface SpeechPort {

    Assessment assess(byte[] wav, AssessmentRequest request);

    byte[] synthesize(String ssml, String format);
}
