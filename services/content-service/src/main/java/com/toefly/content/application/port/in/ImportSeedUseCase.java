package com.toefly.content.application.port.in;

import java.util.Optional;

public interface ImportSeedUseCase {

    ImportReport run(Optional<String> sourceCode, boolean dryRun);
}
