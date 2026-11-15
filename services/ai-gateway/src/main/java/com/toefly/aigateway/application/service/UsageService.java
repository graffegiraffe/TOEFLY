package com.toefly.aigateway.application.service;

import com.toefly.aigateway.application.port.in.UsageUseCase;
import com.toefly.aigateway.application.port.out.AiCallRepository;
import com.toefly.aigateway.domain.call.AiCall;
import com.toefly.aigateway.domain.call.AiCallOutcome;
import com.toefly.aigateway.domain.call.CallCursor;
import com.toefly.aigateway.domain.call.CallFilter;
import com.toefly.aigateway.domain.call.TokenUsage;
import com.toefly.aigateway.domain.call.UsageRow;
import com.toefly.aigateway.domain.prompt.PromptRuleException;
import com.toefly.aigateway.domain.routing.ModelRef;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Usage reports, the call journal for the admin, erasure on account deletion and the retention of the journal. */
@Service
class UsageService implements UsageUseCase {

    static final int MAX_DAYS = 366;

    private final AiCallRepository calls;
    private final CallJournal journal;
    private final GatewaySettings settings;
    private final Clock clock;

    UsageService(AiCallRepository calls, CallJournal journal, GatewaySettings settings, Clock clock) {
        this.calls = calls;
        this.journal = journal;
        this.settings = settings;
        this.clock = clock;
    }

    @Override
    public String report(UsageReport report) {
        if (report.inputTokens() < 0
                || report.outputTokens() < 0
                || report.audioSeconds() < 0
                || report.characters() < 0) {
            throw new PromptRuleException("validation-failed", "usage cannot be negative");
        }
        String id = journal.nextId();
        journal.record(
                id,
                new CallJournal.Entry(
                        report.callerService(),
                        report.useCase(),
                        report.feature(),
                        null,
                        report.userId(),
                        null,
                        null,
                        new ModelRef(report.provider(), report.model()),
                        0,
                        false,
                        false,
                        null,
                        null,
                        new TokenUsage(
                                report.inputTokens(),
                                report.outputTokens(),
                                report.audioSeconds(),
                                report.characters()),
                        report.latencyMs(),
                        null,
                        AiCallOutcome.SUCCESS,
                        null,
                        null));
        return id;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UsageRow> usage(@Nullable UUID userId, LocalDate from, LocalDate to) {
        if (to.isBefore(from) || from.plusDays(MAX_DAYS).isBefore(to)) {
            throw new PromptRuleException("validation-failed", "the period must be 1.." + MAX_DAYS + " days");
        }
        return calls.usage(userId, from, to);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AiCall> calls(CallFilter filter, @Nullable CallCursor after, int limit) {
        return calls.page(filter, after, Math.clamp(limit, 1, 100));
    }

    @Override
    @Transactional
    public void deleteUser(UUID userId) {
        calls.deleteUser(userId);
    }

    @Override
    @Transactional
    public void applyRetention() {
        calls.clearPayloads(clock.instant().minus(settings.payloadRetention()));
        calls.deleteOlderThan(clock.instant().minus(settings.rowRetention()));
    }
}
