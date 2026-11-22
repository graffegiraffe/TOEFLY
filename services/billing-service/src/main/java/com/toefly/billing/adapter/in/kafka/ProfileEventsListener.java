package com.toefly.billing.adapter.in.kafka;

import com.toefly.billing.application.port.in.Caller;
import com.toefly.billing.application.port.in.LifecycleUseCase;
import com.toefly.common.events.ToeflyListener;
import com.toefly.common.outbox.ProcessedEventGuard;
import com.toefly.events.common.Role;
import com.toefly.events.profile.AccountDeletionRequestedV1;
import com.toefly.events.profile.ProfileCreatedV1;
import java.util.EnumSet;
import java.util.Set;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

/** profile-created starts the Pro trial (AC-27.02.1); the erasure removes billing data (docs/04 §5.11). */
@Component
public class ProfileEventsListener {

    private final ProcessedEventGuard guard;
    private final LifecycleUseCase lifecycle;

    public ProfileEventsListener(ProcessedEventGuard guard, LifecycleUseCase lifecycle) {
        this.guard = guard;
        this.lifecycle = lifecycle;
    }

    @ToeflyListener(id = "billing-profile-created", topics = "toefly.profile.profile-created.v1")
    void onProfileCreated(ConsumerRecord<String, ProfileCreatedV1> record) {
        ProfileCreatedV1 event = record.value();
        Set<Caller.Role> roles = EnumSet.noneOf(Caller.Role.class);
        for (Role role : event.getRoles()) {
            switch (role) {
                case STUDENT -> roles.add(Caller.Role.STUDENT);
                case TUTOR -> roles.add(Caller.Role.TUTOR);
                case ADMIN -> roles.add(Caller.Role.ADMIN);
                default -> {}
            }
        }
        guard.runOnce(
                record,
                "ProfileEventsListener.profileCreated",
                () -> lifecycle.profileCreated(event.getUserId(), roles, event.getCorrelationId(), event.getEventId()));
    }

    @ToeflyListener(id = "billing-account-deletion", topics = "toefly.profile.account-deletion-requested.v1")
    void onDeletion(ConsumerRecord<String, AccountDeletionRequestedV1> record) {
        guard.runOnce(
                record,
                "ProfileEventsListener.deletion",
                () -> lifecycle.accountDeletionRequested(record.value().getUserId()));
    }
}
