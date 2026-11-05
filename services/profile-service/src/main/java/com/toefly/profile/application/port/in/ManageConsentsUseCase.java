package com.toefly.profile.application.port.in;

import com.toefly.profile.domain.consent.Consent;
import com.toefly.profile.domain.consent.ConsentSource;
import com.toefly.profile.domain.consent.ConsentType;
import java.util.List;

public interface ManageConsentsUseCase {

    /** The latest entry of every consent type the user has answered. */
    List<Consent> currentConsents(SignedInUser user);

    Consent recordConsent(SignedInUser user, ConsentType type, boolean granted, ConsentSource source);
}
