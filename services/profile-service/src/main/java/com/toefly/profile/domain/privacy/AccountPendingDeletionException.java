package com.toefly.profile.domain.privacy;

import com.toefly.common.web.ProblemType;

@ProblemType(value = "account-pending-deletion", status = 409)
public class AccountPendingDeletionException extends RuntimeException {

    public AccountPendingDeletionException() {
        super("The account is already being deleted.");
    }
}
