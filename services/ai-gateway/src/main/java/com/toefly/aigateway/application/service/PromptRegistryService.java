package com.toefly.aigateway.application.service;

import com.toefly.aigateway.application.port.in.EvalsNotPassedException;
import com.toefly.aigateway.application.port.in.PromptNotFoundException;
import com.toefly.aigateway.application.port.in.PromptRegistryUseCase;
import com.toefly.aigateway.application.port.out.EvalRunRepository;
import com.toefly.aigateway.application.port.out.PromptRepository;
import com.toefly.aigateway.application.port.out.PromptSourcePort;
import com.toefly.aigateway.domain.prompt.PromptRuleException;
import com.toefly.aigateway.domain.prompt.PromptStatus;
import com.toefly.aigateway.domain.prompt.PromptTemplate;
import com.toefly.aigateway.domain.prompt.PromptVersion;
import com.toefly.aigateway.domain.safety.GuardPolicy;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registry of prompts (§2.9): files of the repository arrive as drafts, a passed eval run makes a draft a
 * candidate, an admin activates a version (the previous one is archived), rollback is activation of an older one.
 */
@Service
class PromptRegistryService implements PromptRegistryUseCase {

    private final PromptRepository prompts;
    private final PromptSourcePort sources;
    private final EvalRunRepository evals;
    private final GatewaySettings settings;
    private final Clock clock;

    PromptRegistryService(
            PromptRepository prompts,
            PromptSourcePort sources,
            EvalRunRepository evals,
            GatewaySettings settings,
            Clock clock) {
        this.prompts = prompts;
        this.sources = sources;
        this.evals = evals;
        this.settings = settings;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PromptTemplate> templates() {
        return prompts.templates();
    }

    @Override
    @Transactional(readOnly = true)
    public List<VersionView> versions(String key) {
        PromptTemplate template = template(key);
        return prompts.versions(key).stream()
                .sorted(Comparator.comparingInt(PromptVersion::version).reversed())
                .map(version -> new VersionView(
                        version,
                        evals.latestPassed(key, version.version()),
                        Integer.valueOf(version.version()).equals(template.activeVersion())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PromptVersion active(String key) {
        PromptTemplate template = template(key);
        if (template.activeVersion() == null) {
            throw new PromptNotFoundException(key, key + " has no active version.");
        }
        return prompts.version(key, template.activeVersion())
                .orElseThrow(() -> new PromptNotFoundException(key, "the active version of " + key + " is missing"));
    }

    @Override
    @Transactional
    public PromptVersion createVersion(String key, NewVersion draft, String actor) {
        PromptTemplate template = template(key);
        int next = prompts.versions(key).stream()
                        .mapToInt(PromptVersion::version)
                        .max()
                        .orElse(0)
                + 1;
        PromptVersion version = new PromptVersion(
                key,
                next,
                template.useCase(),
                draft.description(),
                draft.system(),
                draft.user(),
                draft.outputSchema(),
                draft.params(),
                draft.maxInputTokens() == null ? PromptVersion.DEFAULT_MAX_INPUT_TOKENS : draft.maxInputTokens(),
                draft.untrusted(),
                draft.guardPolicy() == null ? GuardPolicy.NONE : draft.guardPolicy(),
                draft.evalSuite(),
                PromptStatus.DRAFT,
                GenerationService.sha256(draft.system() + "\u0000" + draft.user() + "\u0000" + draft.outputSchema()),
                actor,
                clock.instant());
        if (version.system().isBlank() || version.user().isBlank()) {
            throw new PromptRuleException("validation-failed", "system and user templates are required");
        }
        // A repeated request (same texts) returns the draft it created instead of a second version.
        var latest = prompts.versions(key).stream().max(Comparator.comparingInt(PromptVersion::version));
        if (latest.isPresent()
                && latest.get().sourceHash().equals(version.sourceHash())
                && latest.get().status() == PromptStatus.DRAFT) {
            return latest.get();
        }
        prompts.add(version);
        return version;
    }

    @Override
    @Transactional
    public PromptVersion activate(String key, int number, String actor) {
        PromptTemplate template = template(key);
        PromptVersion version = prompts.version(key, number)
                .orElseThrow(() -> new PromptNotFoundException(key, "No version " + number + " of " + key + "."));
        if (settings.requireEvals() && evals.latestPassed(key, number).isEmpty()) {
            throw new EvalsNotPassedException(key, number);
        }
        Integer previous = template.activeVersion();
        if (previous != null && previous != number) {
            prompts.updateStatus(key, previous, PromptStatus.ARCHIVED);
        }
        prompts.updateStatus(key, number, PromptStatus.ACTIVE);
        prompts.save(template.activate(number));
        return version.withStatus(PromptStatus.ACTIVE);
    }

    @Override
    @Transactional
    public int syncSources() {
        int added = 0;
        for (PromptSourcePort.Source source : sources.load()) {
            PromptTemplate template = prompts.template(source.template().key()).orElse(null);
            if (template == null) {
                template = source.template();
                prompts.save(template);
            }
            PromptVersion version = source.version();
            if (prompts.version(version.key(), version.version()).isEmpty()) {
                prompts.add(version.withStatus(PromptStatus.DRAFT));
                added++;
            }
            if (settings.activateOnSync() && template.activeVersion() == null) {
                prompts.updateStatus(version.key(), version.version(), PromptStatus.ACTIVE);
                template = template.activate(version.version());
                prompts.save(template);
            }
        }
        return added;
    }

    private PromptTemplate template(String key) {
        return prompts.template(key)
                .orElseThrow(() -> new PromptNotFoundException(key, "No prompt " + key + " in the registry."));
    }
}
