package com.toefly.aigateway.application.port.out;

import com.toefly.aigateway.domain.safety.PiiSpan;
import java.util.List;

/** Stage 3 of masking: people, places and organisations found by a local model; throws {@link ProviderFailure}. */
public interface NerPort {

    List<PiiSpan> find(String text);
}
