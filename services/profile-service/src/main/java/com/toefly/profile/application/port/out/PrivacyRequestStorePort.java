package com.toefly.profile.application.port.out;

import com.toefly.profile.domain.privacy.AccountDeletion;
import com.toefly.profile.domain.privacy.DataExport;
import java.util.Optional;
import java.util.UUID;

public interface PrivacyRequestStorePort {

    Optional<DataExport> latestExport(UUID userId);

    Optional<DataExport> findExport(UUID userId, UUID exportId);

    void saveExport(DataExport export);

    Optional<AccountDeletion> latestDeletion(UUID userId);

    void saveDeletion(AccountDeletion deletion);
}
