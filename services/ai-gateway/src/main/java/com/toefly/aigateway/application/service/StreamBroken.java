package com.toefly.aigateway.application.service;

import com.toefly.aigateway.application.port.out.ProviderFailure;

/** A stream failed after it had sent text: falling back would repeat that text, so the call ends. */
final class StreamBroken extends RuntimeException {

    StreamBroken(ProviderFailure cause) {
        super("the stream failed after the first delta", cause);
    }
}
