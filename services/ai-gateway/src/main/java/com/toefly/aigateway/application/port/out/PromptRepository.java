package com.toefly.aigateway.application.port.out;

import com.toefly.aigateway.domain.prompt.PromptTemplate;
import com.toefly.aigateway.domain.prompt.PromptVersion;
import java.util.List;
import java.util.Optional;

/** Registry of prompts (tables {@code prompt_template}, {@code prompt_version}). */
public interface PromptRepository {

    Optional<PromptTemplate> template(String key);

    List<PromptTemplate> templates();

    Optional<PromptVersion> version(String key, int version);

    List<PromptVersion> versions(String key);

    void save(PromptTemplate template);

    /** Adds a version; versions are never overwritten. */
    void add(PromptVersion version);

    void updateStatus(String key, int version, com.toefly.aigateway.domain.prompt.PromptStatus status);
}
