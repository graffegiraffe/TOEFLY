package com.toefly.aigateway.adapter.out.provider;

import com.toefly.aigateway.application.port.out.AudioSourcePort;
import com.toefly.aigateway.application.port.out.ProviderFailure;
import org.springframework.web.client.RestClient;

/** Reads a recording by the presigned URL speech-service passes; at most 25 MB (Whisper's limit). */
public class HttpAudioSourceAdapter implements AudioSourcePort {

    static final int MAX_BYTES = 25 * 1024 * 1024;

    private final RestClient http;

    public HttpAudioSourceAdapter(RestClient http) {
        this.http = http;
    }

    @Override
    public byte[] fetch(String url) {
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            throw new ProviderFailure(ProviderFailure.Kind.REJECTED, "audioUrl must be http(s)", null, null);
        }
        byte[] audio;
        try {
            audio = http.get().uri(java.net.URI.create(url)).retrieve().body(byte[].class);
        } catch (RuntimeException error) {
            throw ProviderErrors.map("AUDIO_SOURCE", error);
        }
        if (audio == null || audio.length == 0) {
            throw new ProviderFailure(ProviderFailure.Kind.REJECTED, "the recording is empty", null, null);
        }
        if (audio.length > MAX_BYTES) {
            throw new ProviderFailure(ProviderFailure.Kind.REJECTED, "the recording exceeds 25 MB", null, null);
        }
        return audio;
    }
}
