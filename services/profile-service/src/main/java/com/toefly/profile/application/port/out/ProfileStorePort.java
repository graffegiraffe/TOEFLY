package com.toefly.profile.application.port.out;

import com.toefly.profile.domain.profile.UserProfile;
import java.util.Optional;
import java.util.UUID;

public interface ProfileStorePort {

    Optional<UserProfile> findById(UUID userId);

    /** Inserts a new profile; false when a concurrent request created it first. */
    boolean insertIfAbsent(UserProfile profile);

    /** Saves a changed profile if the stored one still has {@code previousVersion}; fails on a lost update. */
    void update(UserProfile profile, long previousVersion);

    /** Appends the current goal to the goal history under its revision. */
    void appendGoalRevision(UserProfile profile);
}
