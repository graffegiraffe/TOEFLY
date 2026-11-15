package com.toefly.aigateway.application.port.out;

import com.toefly.aigateway.domain.prompt.PromptTemplate;
import com.toefly.aigateway.domain.prompt.PromptVersion;
import java.util.List;

/** Prompt files of the repository, {@code prompts/<promptKey>/v<N>.yaml} (§2.9). */
public interface PromptSourcePort {

    List<Source> load();

    record Source(PromptTemplate template, PromptVersion version) {}
}
