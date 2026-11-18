package com.toefly.aigateway.adapter.in.web;

import com.toefly.aigateway.adapter.in.web.dto.AiCallPage;
import com.toefly.aigateway.adapter.in.web.dto.AiStamp;
import com.toefly.aigateway.adapter.in.web.dto.AiUsageReport;
import com.toefly.aigateway.adapter.in.web.dto.EvalRun;
import com.toefly.aigateway.adapter.in.web.dto.PageInfo;
import com.toefly.aigateway.adapter.in.web.dto.PromptParams;
import com.toefly.aigateway.adapter.in.web.dto.PromptTemplate;
import com.toefly.aigateway.adapter.in.web.dto.PromptVersion;
import com.toefly.aigateway.adapter.in.web.dto.ProviderStatus;
import com.toefly.aigateway.adapter.in.web.dto.ProviderStatusList;
import com.toefly.aigateway.adapter.in.web.dto.QuotaDimensionStatus;
import com.toefly.aigateway.adapter.in.web.dto.TokenUsage;
import com.toefly.aigateway.adapter.in.web.dto.UsageRow;
import com.toefly.aigateway.application.port.in.QuotaQueryUseCase;
import com.toefly.aigateway.domain.call.AiCall;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Domain values to the DTOs of ai-gateway.yaml. */
final class AiDtoMapper {

    private AiDtoMapper() {}

    static AiStamp stamp(com.toefly.aigateway.domain.call.AiStamp stamp) {
        return new AiStamp()
                .aiCallId(stamp.aiCallId())
                .promptKey(stamp.promptKey())
                .promptVersion(stamp.promptVersion())
                .provider(com.toefly.aigateway.adapter.in.web.dto.AiProvider.fromValue(
                        stamp.provider().name()))
                .model(stamp.model())
                .fallbackDepth(stamp.fallbackDepth())
                .degraded(stamp.degraded())
                .cacheHit(stamp.cacheHit())
                .createdAt(time(stamp.createdAt()));
    }

    static TokenUsage usage(com.toefly.aigateway.domain.call.TokenUsage usage, long latencyMs) {
        return new TokenUsage()
                .inputTokens(usage.inputTokens())
                .outputTokens(usage.outputTokens())
                .latencyMs(latencyMs);
    }

    static PromptTemplate template(com.toefly.aigateway.domain.prompt.PromptTemplate template) {
        return new PromptTemplate()
                .key(template.key())
                .useCase(com.toefly.aigateway.adapter.in.web.dto.AiUseCase.fromValue(
                        template.useCase().name()))
                .description(template.description())
                .ownerService(template.ownerService())
                .activeVersion(template.activeVersion())
                .canaryVersion(template.canaryVersion())
                .canaryPercent(template.canaryPercent());
    }

    static PromptVersion version(
            com.toefly.aigateway.domain.prompt.PromptVersion version,
            @Nullable Boolean active,
            com.toefly.aigateway.domain.prompt.@Nullable EvalRun latest) {
        return new PromptVersion()
                .key(version.key())
                .version(version.version())
                .useCase(com.toefly.aigateway.adapter.in.web.dto.AiUseCase.fromValue(
                        version.useCase().name()))
                .description(version.description())
                .system(version.system())
                .user(version.user())
                .outputSchema(version.outputSchema())
                .params(new PromptParams()
                        .temperature(version.params().temperature())
                        .maxOutputTokens(version.params().maxOutputTokens())
                        .reasoningEffort(
                                version.params().reasoningEffort() == null
                                        ? null
                                        : PromptParams.ReasoningEffortEnum.fromValue(
                                                version.params().reasoningEffort())))
                .maxInputTokens(version.maxInputTokens())
                .untrusted(version.untrusted().stream().sorted().toList())
                .guardPolicy(com.toefly.aigateway.adapter.in.web.dto.GuardPolicy.fromValue(
                        version.guardPolicy().name()))
                .evalSuite(version.evalSuite())
                .status(com.toefly.aigateway.adapter.in.web.dto.PromptStatus.fromValue(
                        version.status().name()))
                .active(active)
                .latestEval(latest == null ? null : evalRun(latest))
                .createdBy(version.createdBy())
                .createdAt(time(version.createdAt()));
    }

    static EvalRun evalRun(com.toefly.aigateway.domain.prompt.EvalRun run) {
        return new EvalRun()
                .id(run.id())
                .promptKey(run.promptKey())
                .promptVersion(run.promptVersion())
                .suite(run.suite())
                .mode(run.mode())
                .passed(run.passed())
                .metrics(new java.util.HashMap<>(run.metrics()))
                .reportUrl(run.reportUrl())
                .ranAt(time(run.ranAt()))
                .receivedAt(time(run.receivedAt()))
                .thresholds(run.thresholds().entrySet().stream()
                        .collect(java.util.stream.Collectors.toMap(
                                java.util.Map.Entry::getKey,
                                e -> new com.toefly.aigateway.adapter.in.web.dto.EvalThreshold()
                                        .min(e.getValue().min())
                                        .max(e.getValue().max()))))
                .cases(run.cases().stream()
                        .map(c -> new com.toefly.aigateway.adapter.in.web.dto.EvalCaseResult()
                                .caseId(c.caseId())
                                .split(c.split())
                                .gold(c.gold())
                                .predicted(c.predicted())
                                .passes(c.passes())
                                .flags(c.flags()))
                        .toList());
    }

    static java.util.Map<String, com.toefly.aigateway.domain.prompt.EvalRun.Bound> thresholds(
            java.util.@Nullable Map<String, com.toefly.aigateway.adapter.in.web.dto.EvalThreshold> thresholds) {
        if (thresholds == null) {
            return java.util.Map.of();
        }
        java.util.Map<String, com.toefly.aigateway.domain.prompt.EvalRun.Bound> bounds = new java.util.HashMap<>();
        thresholds.forEach((name, t) -> bounds.put(
                name,
                new com.toefly.aigateway.domain.prompt.EvalRun.Bound(
                        t.getMin() == null ? null : t.getMin().doubleValue(),
                        t.getMax() == null ? null : t.getMax().doubleValue())));
        return bounds;
    }

    static List<com.toefly.aigateway.domain.prompt.EvalRun.CaseResult> cases(
            @Nullable List<com.toefly.aigateway.adapter.in.web.dto.EvalCaseResult> cases) {
        if (cases == null) {
            return List.of();
        }
        return cases.stream()
                .map(c -> new com.toefly.aigateway.domain.prompt.EvalRun.CaseResult(
                        c.getCaseId(),
                        c.getSplit(),
                        c.getGold() == null ? null : c.getGold().doubleValue(),
                        c.getPredicted().doubleValue(),
                        c.getPasses() == null ? List.of() : c.getPasses(),
                        c.getFlags() == null ? List.of() : c.getFlags()))
                .toList();
    }

    static ProviderStatusList providers(List<QuotaQueryUseCase.ModelStatus> statuses) {
        return new ProviderStatusList()
                .items(statuses.stream()
                        .map(status -> new ProviderStatus()
                                .provider(com.toefly.aigateway.adapter.in.web.dto.AiProvider.fromValue(
                                        status.model().provider().name()))
                                .model(status.model().model())
                                .circuit(ProviderStatus.CircuitEnum.fromValue(status.circuit()))
                                .dimensions(status.dimensions().stream()
                                        .map(d -> new QuotaDimensionStatus()
                                                .dimension(
                                                        com.toefly.aigateway.adapter.in.web.dto.QuotaDimension
                                                                .fromValue(d.dimension()
                                                                .name()))
                                                .capacity(d.capacity())
                                                .remaining(Math.floor(d.remaining())))
                                        .toList()))
                        .toList());
    }

    static AiUsageReport usage(LocalDate from, LocalDate to, List<com.toefly.aigateway.domain.call.UsageRow> rows) {
        return new AiUsageReport()
                .from(from)
                .to(to)
                .rows(rows.stream()
                        .map(row -> new UsageRow()
                                .day(row.day())
                                .provider(com.toefly.aigateway.adapter.in.web.dto.AiProvider.fromValue(row.provider()))
                                .model(row.model())
                                .useCase(com.toefly.aigateway.adapter.in.web.dto.AiUseCase.fromValue(row.useCase()))
                                .feature(row.feature())
                                .calls(row.calls())
                                .cacheHits(row.cacheHits())
                                .fallbacks(row.fallbacks())
                                .inputTokens(row.inputTokens())
                                .outputTokens(row.outputTokens())
                                .audioSeconds(row.audioSeconds())
                                .characters(row.characters())
                                .shadowCostMicroUsd(row.shadowCostMicroUsd()))
                        .toList());
    }

    static com.toefly.aigateway.adapter.in.web.dto.AiCall call(AiCall call) {
        return new com.toefly.aigateway.adapter.in.web.dto.AiCall()
                .id(call.id())
                .callerService(call.callerService())
                .useCase(com.toefly.aigateway.adapter.in.web.dto.AiUseCase.fromValue(
                        call.useCase().name()))
                .feature(call.feature())
                .priority(
                        call.priority() == null
                                ? null
                                : com.toefly.aigateway.adapter.in.web.dto.Priority.fromValue(
                                        call.priority().name()))
                .userId(call.userId())
                .promptKey(call.promptKey())
                .promptVersion(call.promptVersion())
                .provider(com.toefly.aigateway.adapter.in.web.dto.AiProvider.fromValue(
                        call.provider().name()))
                .model(call.model())
                .fallbackDepth(call.fallbackDepth())
                .degraded(call.degraded())
                .cacheHit(call.cacheHit())
                .requestMasked(call.requestMasked())
                .responseMasked(call.responseMasked())
                .inputTokens(call.usage().inputTokens())
                .outputTokens(call.usage().outputTokens())
                .audioSeconds(call.usage().audioSeconds())
                .characters(call.usage().characters())
                .latencyMs(call.latencyMs())
                .shadowCostMicroUsd(call.shadowCostMicroUsd())
                .guardVerdict(call.guardVerdict())
                .outcome(com.toefly.aigateway.adapter.in.web.dto.AiCallOutcome.fromValue(
                        call.outcome().name()))
                .errorCode(call.errorCode())
                .traceId(call.traceId())
                .createdAt(time(call.createdAt()));
    }

    static AiCallPage page(List<AiCall> calls, int limit, @Nullable String nextCursor) {
        return new AiCallPage()
                .items(calls.stream().map(AiDtoMapper::call).toList())
                .page(new PageInfo().hasMore(nextCursor != null).limit(limit).nextCursor(nextCursor));
    }

    static OffsetDateTime time(java.time.Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
