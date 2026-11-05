package com.toefly.profile.application.port.out;

import com.toefly.profile.domain.consent.Consent;
import java.util.List;
import java.util.UUID;

public interface ConsentStorePort {

    void append(Consent consent);

    List<Consent> latestPerType(UUID userId);
}
