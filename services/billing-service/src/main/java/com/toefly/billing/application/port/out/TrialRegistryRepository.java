package com.toefly.billing.application.port.out;

import java.time.Instant;

/** AC-27.02.4: irreversible hashes of identities that already had a trial. */
public interface TrialRegistryRepository {

    /** Registers the hash; false when it was registered after {@code since}. */
    boolean register(String identityHash, Instant now, Instant since);
}
