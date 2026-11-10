package com.toefly.practice.adapter.in.kafka;

import com.toefly.common.events.ToeflyListener;
import com.toefly.common.outbox.ProcessedEventGuard;
import com.toefly.events.profile.AccountDeletionRequestedV1;
import com.toefly.practice.application.port.in.UserDataUseCase;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

/** GDPR erasure: every stream, projection and time entry of the user (docs/03-domain-model.md §14). */
@Component
public class AccountDeletionListener {

    private final ProcessedEventGuard guard;
    private final UserDataUseCase userData;

    public AccountDeletionListener(ProcessedEventGuard guard, UserDataUseCase userData) {
        this.guard = guard;
        this.userData = userData;
    }

    @ToeflyListener(id = "account-deletion-requested", topics = "toefly.profile.account-deletion-requested.v1")
    void on(ConsumerRecord<String, AccountDeletionRequestedV1> record) {
        guard.runOnce(
                record,
                "AccountDeletionListener",
                () -> userData.erase(record.value().getUserId()));
    }
}
