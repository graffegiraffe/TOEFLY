package com.toefly.analytics.application.port.in;

import java.util.UUID;

/** Account deletion (INV-ANL-05): rows of the learner leave every read model and the journal. */
public interface ForgetLearnerUseCase {

    void forget(UUID userId);
}
