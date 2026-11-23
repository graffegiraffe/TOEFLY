package com.toefly.tutoragent.application.port.out;

import com.toefly.tutoragent.domain.chat.PendingAction;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Changes proposed by the agent and waiting for confirmation. */
public interface PendingActionRepository {

    void save(PendingAction action);

    Optional<PendingAction> find(UUID actionId);

    List<PendingAction> ofMessage(UUID messageId);
}
