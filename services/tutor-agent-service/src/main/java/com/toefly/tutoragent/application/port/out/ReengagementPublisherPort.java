package com.toefly.tutoragent.application.port.out;

import com.toefly.tutoragent.domain.learner.ExplanationLanguage;
import com.toefly.tutoragent.domain.reengagement.ComeBackMessage;
import com.toefly.tutoragent.domain.reengagement.Situation;
import java.time.Instant;
import java.util.UUID;

/** reengagement-message-created through the outbox; notification-service delivers it (phase 11b). */
public interface ReengagementPublisherPort {

    void messageCreated(
            UUID messageId,
            UUID userId,
            Situation situation,
            ComeBackMessage message,
            ExplanationLanguage language,
            boolean aiFallback,
            Instant at);
}
