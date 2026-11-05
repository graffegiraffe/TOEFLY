package com.toefly.profile.application.port.in;

import com.toefly.profile.domain.privacy.AccountDeletion;
import java.util.Optional;

public interface RequestAccountDeletionUseCase {

    /** Disables the account, revokes its sessions and publishes account-deletion-requested (AC-31.04.1). */
    AccountDeletion requestDeletion(SignedInUser user, String confirmation);

    Optional<AccountDeletion> currentDeletion(SignedInUser user);
}
