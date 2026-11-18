package com.toefly.aigateway.adapter.in.web;

import com.toefly.aigateway.adapter.in.web.api.AdminAiApi;
import com.toefly.aigateway.adapter.in.web.dto.AiCallOutcome;
import com.toefly.aigateway.adapter.in.web.dto.AiCallPage;
import com.toefly.aigateway.adapter.in.web.dto.AiProvider;
import com.toefly.aigateway.adapter.in.web.dto.AiUsageReport;
import com.toefly.aigateway.adapter.in.web.dto.CreatePromptVersionRequest;
import com.toefly.aigateway.adapter.in.web.dto.EvalRun;
import com.toefly.aigateway.adapter.in.web.dto.EvalRunPage;
import com.toefly.aigateway.adapter.in.web.dto.EvalRunReport;
import com.toefly.aigateway.adapter.in.web.dto.PromptTemplatePage;
import com.toefly.aigateway.adapter.in.web.dto.PromptVersion;
import com.toefly.aigateway.adapter.in.web.dto.PromptVersionList;
import com.toefly.aigateway.adapter.in.web.dto.ProviderStatusList;
import com.toefly.aigateway.application.port.in.EvalRunUseCase;
import com.toefly.aigateway.application.port.in.PromptRegistryUseCase;
import com.toefly.aigateway.application.port.in.QuotaQueryUseCase;
import com.toefly.aigateway.application.port.in.UsageUseCase;
import com.toefly.aigateway.domain.call.AiCall;
import com.toefly.aigateway.domain.call.CallCursor;
import com.toefly.aigateway.domain.call.CallFilter;
import com.toefly.aigateway.domain.prompt.PromptParams;
import com.toefly.aigateway.domain.prompt.PromptRuleException;
import com.toefly.aigateway.domain.safety.GuardPolicy;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** {@code /admin/ai/**} (role ADMIN; eval reports also with the CI service token). */
@RestController
public class AdminAiController implements AdminAiApi {

    private final PromptRegistryUseCase prompts;
    private final UsageUseCase usage;
    private final QuotaQueryUseCase quotas;
    private final EvalRunUseCase evals;

    public AdminAiController(
            PromptRegistryUseCase prompts, UsageUseCase usage, QuotaQueryUseCase quotas, EvalRunUseCase evals) {
        this.prompts = prompts;
        this.usage = usage;
        this.quotas = quotas;
        this.evals = evals;
    }

    @Override
    public ResponseEntity<PromptTemplatePage> listPrompts() {
        return ResponseEntity.ok(new PromptTemplatePage()
                .items(prompts.templates().stream().map(AiDtoMapper::template).toList()));
    }

    @Override
    public ResponseEntity<PromptVersionList> listPromptVersions(String promptKey) {
        return ResponseEntity.ok(new PromptVersionList()
                .items(prompts.versions(promptKey).stream()
                        .map(view -> AiDtoMapper.version(
                                view.version(), view.active(), view.latestEval().orElse(null)))
                        .toList()));
    }

    @Override
    public ResponseEntity<PromptVersion> createPromptVersion(
            String promptKey, CreatePromptVersionRequest request, @Nullable UUID idempotencyKey) {
        var params = request.getParams();
        var version = prompts.createVersion(
                promptKey,
                new PromptRegistryUseCase.NewVersion(
                        request.getDescription(),
                        request.getSystem(),
                        request.getUser(),
                        request.getOutputSchema(),
                        params == null
                                ? PromptParams.NONE
                                : new PromptParams(
                                        params.getTemperature(),
                                        params.getMaxOutputTokens(),
                                        params.getReasoningEffort() == null
                                                ? null
                                                : params.getReasoningEffort().getValue()),
                        request.getMaxInputTokens(),
                        new HashSet<>(request.getUntrusted()),
                        request.getGuardPolicy() == null
                                ? null
                                : GuardPolicy.valueOf(request.getGuardPolicy().getValue()),
                        request.getEvalSuite()),
                Callers.actor());
        return ResponseEntity.status(HttpStatus.CREATED).body(AiDtoMapper.version(version, false, null));
    }

    @Override
    public ResponseEntity<PromptVersion> activatePromptVersion(
            String promptKey, Integer version, @Nullable UUID idempotencyKey) {
        return ResponseEntity.ok(
                AiDtoMapper.version(prompts.activate(promptKey, version, Callers.actor()), true, null));
    }

    @Override
    public ResponseEntity<AiCallPage> listAiCalls(
            @Nullable String promptKey,
            @Nullable AiProvider provider,
            @Nullable AiCallOutcome outcome,
            @Nullable OffsetDateTime from,
            @Nullable OffsetDateTime to,
            @Nullable String cursor,
            Integer limit) {
        List<AiCall> page = usage.calls(
                new CallFilter(
                        promptKey,
                        provider == null ? null : provider.getValue(),
                        outcome == null ? null : outcome.getValue(),
                        from == null ? null : from.toInstant(),
                        to == null ? null : to.toInstant()),
                decode(cursor),
                limit + 1);
        boolean more = page.size() > limit;
        List<AiCall> shown = more ? page.subList(0, limit) : page;
        String next = more ? encode(shown.getLast()) : null;
        return ResponseEntity.ok(AiDtoMapper.page(shown, limit, next));
    }

    @Override
    public ResponseEntity<ProviderStatusList> listProviderStatuses() {
        return ResponseEntity.ok(AiDtoMapper.providers(quotas.snapshot()));
    }

    @Override
    public ResponseEntity<AiUsageReport> getAiUsage(LocalDate from, LocalDate to) {
        return ResponseEntity.ok(AiDtoMapper.usage(from, to, usage.usage(null, from, to)));
    }

    @Override
    public ResponseEntity<EvalRunPage> listEvalRuns(@Nullable String promptKey, Integer limit) {
        return ResponseEntity.ok(new EvalRunPage()
                .items(evals.page(promptKey, limit).stream()
                        .map(AiDtoMapper::evalRun)
                        .map(run -> run.cases(null))
                        .toList()));
    }

    @Override
    public ResponseEntity<EvalRun> getEvalRun(java.util.UUID runId) {
        return evals.find(runId)
                .map(AiDtoMapper::evalRun)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<EvalRun> recordEvalRun(EvalRunReport report) {
        var run = evals.record(new EvalRunUseCase.EvalReport(
                report.getPromptKey(),
                report.getPromptVersion(),
                report.getSuite(),
                report.getMode().getValue(),
                report.getPassed(),
                report.getMetrics() == null ? java.util.Map.of() : report.getMetrics(),
                report.getReportUrl(),
                report.getRanAt().toInstant(),
                AiDtoMapper.thresholds(report.getThresholds()),
                AiDtoMapper.cases(report.getCases())));
        return ResponseEntity.status(HttpStatus.CREATED).body(AiDtoMapper.evalRun(run));
    }

    static String encode(AiCall last) {
        String raw = last.createdAt().toString() + "|" + last.id();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    static @Nullable CallCursor decode(@Nullable String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int bar = raw.indexOf('|');
            return new CallCursor(Instant.parse(raw.substring(0, bar)), raw.substring(bar + 1));
        } catch (RuntimeException broken) {
            throw new PromptRuleException("validation-failed", "invalid cursor");
        }
    }
}
