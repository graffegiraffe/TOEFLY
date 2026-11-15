package com.toefly.aigateway.application.port.out;

/** Downloads a recording by its presigned URL; throws {@link ProviderFailure} when the object is unreachable. */
public interface AudioSourcePort {

    byte[] fetch(String url);
}
