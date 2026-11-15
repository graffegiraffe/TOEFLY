package com.toefly.aigateway.adapter.in.kafka;

import com.toefly.aigateway.application.port.in.UsageUseCase;
import com.toefly.common.events.ToeflyListener;
import com.toefly.common.outbox.ProcessedEventGuard;
import com.toefly.events.profile.AccountDeletionRequestedV1;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

/** Erasure: journal rows and daily aggregates of the user go (§2.8); analytics keeps only anonymous sums. */
@Component
public class ProfileEventsListener {

    private final ProcessedEventGuard guard;
    private final UsageUseCase usage;

    public ProfileEventsListener(ProcessedEventGuard guard, UsageUseCase usage) {
        this.guard = guard;
        this.usage = usage;
    }

    @ToeflyListener(id = "account-deletion-requested", topics = "toefly.profile.account-deletion-requested.v1")
    void onDeletion(ConsumerRecord<String, AccountDeletionRequestedV1> record) {
        guard.runOnce(
                record,
                "ProfileEventsListener.deletion",
                () -> usage.deleteUser(record.value().getUserId()));
    }
}
