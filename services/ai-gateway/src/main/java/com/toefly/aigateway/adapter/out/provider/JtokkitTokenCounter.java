package com.toefly.aigateway.adapter.out.provider;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import com.toefly.aigateway.application.port.out.TokenCounterPort;
import com.toefly.aigateway.domain.routing.ModelRef;

/** {@code o200k_base} tokens with a margin of 10%, 25% for qwen whose tokenizer differs (§2.5.1). */
public class JtokkitTokenCounter implements TokenCounterPort {

    private final Encoding encoding = Encodings.newDefaultEncodingRegistry().getEncoding(EncodingType.O200K_BASE);

    @Override
    public int estimate(String text, ModelRef model) {
        double margin = model.model().startsWith("qwen") ? 1.25 : 1.10;
        return (int) Math.ceil(encoding.countTokensOrdinary(text) * margin);
    }
}
