package com.toefly.aigateway.application.service;

import com.toefly.aigateway.application.port.in.EvalRunUseCase;
import com.toefly.aigateway.application.port.in.PromptNotFoundException;
import com.toefly.aigateway.application.port.out.EvalRunRepository;
import com.toefly.aigateway.application.port.out.PromptRepository;
import com.toefly.aigateway.domain.prompt.EvalRun;
import com.toefly.aigateway.domain.prompt.PromptStatus;
import com.toefly.aigateway.domain.prompt.PromptVersion;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Accepts eval reports from CI; a passed run promotes a draft to CANDIDATE (§7.4). */
@Service
class EvalRunService implements EvalRunUseCase {

    private final EvalRunRepository runs;
    private final PromptRepository prompts;
    private final Clock clock;

    EvalRunService(EvalRunRepository runs, PromptRepository prompts, Clock clock) {
        this.runs = runs;
        this.prompts = prompts;
        this.clock = clock;
    }

    @Override
    @Transactional
    public EvalRun record(EvalReport report) {
        PromptVersion version = prompts.version(report.promptKey(), report.promptVersion())
                .orElseThrow(() -> new PromptNotFoundException(
                        report.promptKey(), "No version " + report.promptVersion() + " of " + report.promptKey()));
        EvalRun run = new EvalRun(
                UUID.randomUUID(),
                report.promptKey(),
                report.promptVersion(),
                report.suite(),
                report.mode(),
                report.passed(),
                report.metrics(),
                report.reportUrl(),
                report.ranAt(),
                clock.instant());
        runs.save(run);
        if (report.passed() && version.status() == PromptStatus.DRAFT) {
            prompts.updateStatus(version.key(), version.version(), PromptStatus.CANDIDATE);
        }
        return run;
    }

    @Override
    @Transactional(readOnly = true)
    public List<EvalRun> page(@Nullable String promptKey, int limit) {
        return runs.page(promptKey, limit);
    }
}
