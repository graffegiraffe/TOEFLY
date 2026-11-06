package com.toefly.content.application.port.in;

import com.toefly.content.application.service.ImportReport;
import java.util.Optional;

public interface ImportSeedUseCase {

    ImportReport run(Optional<String> sourceCode, boolean dryRun);
}
