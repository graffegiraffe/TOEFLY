package com.toefly.profile.application.port.in;

import com.toefly.profile.domain.privacy.DataExport;
import java.util.Optional;
import java.util.UUID;

public interface RequestDataExportUseCase {

    DataExport requestExport(SignedInUser user);

    Optional<DataExport> findExport(SignedInUser user, UUID exportId);
}
