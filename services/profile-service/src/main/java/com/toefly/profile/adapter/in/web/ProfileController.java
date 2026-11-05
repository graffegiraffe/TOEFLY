package com.toefly.profile.adapter.in.web;

import com.toefly.profile.adapter.in.web.api.PreferencesApi;
import com.toefly.profile.adapter.in.web.api.ProfileApi;
import com.toefly.profile.adapter.in.web.dto.Preferences;
import com.toefly.profile.adapter.in.web.dto.UpdateProfileRequest;
import com.toefly.profile.adapter.in.web.dto.UserProfile;
import com.toefly.profile.application.port.in.GetProfileUseCase;
import com.toefly.profile.application.port.in.UpdateProfileUseCase;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProfileController implements ProfileApi, PreferencesApi {

    private final GetProfileUseCase getProfile;
    private final UpdateProfileUseCase updateProfile;
    private final CurrentUser currentUser;

    public ProfileController(
            GetProfileUseCase getProfile, UpdateProfileUseCase updateProfile, CurrentUser currentUser) {
        this.getProfile = getProfile;
        this.updateProfile = updateProfile;
        this.currentUser = currentUser;
    }

    @Override
    public ResponseEntity<UserProfile> getMe() {
        var profile = getProfile.getOrCreate(currentUser.get());
        return ResponseEntity.ok().eTag(ETags.of(profile.version())).body(ProfileDtos.profile(profile));
    }

    @Override
    public ResponseEntity<UserProfile> updateMe(UpdateProfileRequest request, @Nullable String ifMatch) {
        var profile = updateProfile.rename(currentUser.get(), request.getDisplayName(), ETags.requireVersion(ifMatch));
        return ResponseEntity.ok().eTag(ETags.of(profile.version())).body(ProfileDtos.profile(profile));
    }

    @Override
    public ResponseEntity<Preferences> getPreferences() {
        var profile = getProfile.getOrCreate(currentUser.get());
        return ResponseEntity.ok()
                .eTag(ETags.of(profile.version()))
                .body(ProfileDtos.preferences(profile.preferences()));
    }

    @Override
    public ResponseEntity<Preferences> updatePreferences(Preferences request, @Nullable String ifMatch) {
        var preferences = new com.toefly.profile.domain.profile.Preferences(
                com.toefly.profile.domain.profile.Preferences.zone(request.getTimezone()),
                ProfileDtos.language(request.getExplanationLanguage()),
                request.getMinutesPerDay(),
                Objects.requireNonNull(ProfileDtos.localTime(request.getPreferredStudyTime())));
        var profile = updateProfile.updatePreferences(currentUser.get(), preferences, ETags.requireVersion(ifMatch));
        return ResponseEntity.ok()
                .eTag(ETags.of(profile.version()))
                .body(ProfileDtos.preferences(profile.preferences()));
    }
}
