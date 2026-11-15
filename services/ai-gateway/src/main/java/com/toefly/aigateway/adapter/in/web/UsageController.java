package com.toefly.aigateway.adapter.in.web;

import com.toefly.aigateway.adapter.in.web.api.InternalPromptsApi;
import com.toefly.aigateway.adapter.in.web.api.QuotasApi;
import com.toefly.aigateway.adapter.in.web.api.UsageApi;
import com.toefly.aigateway.adapter.in.web.dto.AiUsageReport;
import com.toefly.aigateway.adapter.in.web.dto.PromptVersion;
import com.toefly.aigateway.adapter.in.web.dto.ProviderStatusList;
import com.toefly.aigateway.adapter.in.web.dto.UsageAccepted;
import com.toefly.aigateway.adapter.in.web.dto.UsageReport;
import com.toefly.aigateway.application.port.in.PromptRegistryUseCase;
import com.toefly.aigateway.application.port.in.QuotaQueryUseCase;
import com.toefly.aigateway.application.port.in.UsageUseCase;
import com.toefly.aigateway.domain.routing.AiProvider;
import com.toefly.aigateway.domain.routing.AiUseCase;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Internal reads and the usage report of calls made outside the gateway. */
@RestController
public class UsageController implements UsageApi, QuotasApi, InternalPromptsApi {

    private final UsageUseCase usage;
    private final QuotaQueryUseCase quotas;
    private final PromptRegistryUseCase prompts;

    public UsageController(UsageUseCase usage, QuotaQueryUseCase quotas, PromptRegistryUseCase prompts) {
        this.usage = usage;
        this.quotas = quotas;
        this.prompts = prompts;
    }

    @Override
    public ResponseEntity<UsageAccepted> reportUsage(UsageReport report) {
        String id = usage.report(new UsageUseCase.UsageReport(
                Callers.service(),
                AiProvider.valueOf(report.getProvider().getValue()),
                report.getModel(),
                AiUseCase.valueOf(report.getUseCase().getValue()),
                report.getUserId(),
                report.getFeature(),
                report.getInputTokens() == null ? 0 : report.getInputTokens(),
                report.getOutputTokens() == null ? 0 : report.getOutputTokens(),
                report.getAudioSeconds() == null ? 0 : report.getAudioSeconds(),
                report.getCharacters() == null ? 0 : report.getCharacters(),
                report.getLatencyMs() == null ? 0 : report.getLatencyMs()));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new UsageAccepted().aiCallId(id));
    }

    @Override
    public ResponseEntity<AiUsageReport> getUserUsage(UUID userId, LocalDate from, LocalDate to) {
        return ResponseEntity.ok(AiDtoMapper.usage(from, to, usage.usage(userId, from, to)));
    }

    @Override
    public ResponseEntity<ProviderStatusList> getQuotas() {
        return ResponseEntity.ok(AiDtoMapper.providers(quotas.snapshot()));
    }

    @Override
    public ResponseEntity<PromptVersion> getActivePrompt(String promptKey) {
        return ResponseEntity.ok(AiDtoMapper.version(prompts.active(promptKey), true, null));
    }
}
