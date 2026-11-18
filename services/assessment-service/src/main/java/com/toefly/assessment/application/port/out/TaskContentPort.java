package com.toefly.assessment.application.port.out;

import java.util.Optional;
import java.util.UUID;

/** Item sets of content-service, read with the service token (docs/05-api-contracts.md §9.3.1). */
public interface TaskContentPort {

    /** Empty when the set does not exist or is not published; throws {@link DependencyUnavailableException}. */
    Optional<TaskContent> load(UUID itemSetId);
}
