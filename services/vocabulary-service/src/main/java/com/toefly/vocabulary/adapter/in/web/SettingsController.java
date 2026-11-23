package com.toefly.vocabulary.adapter.in.web;

import com.toefly.vocabulary.adapter.in.web.api.SettingsApi;
import com.toefly.vocabulary.adapter.in.web.dto.VocabularySettings;
import com.toefly.vocabulary.adapter.in.web.dto.VocabularySettingsRequest;
import com.toefly.vocabulary.application.port.in.DictionaryUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** New cards per day (AC-15.03.1). */
@RestController
public class SettingsController implements SettingsApi {

    private final DictionaryUseCase dictionary;
    private final CurrentUser user;

    public SettingsController(DictionaryUseCase dictionary, CurrentUser user) {
        this.dictionary = dictionary;
        this.user = user;
    }

    @Override
    public ResponseEntity<VocabularySettings> getVocabularySettings() {
        return ResponseEntity.ok(dto(dictionary.settings(user.id())));
    }

    @Override
    public ResponseEntity<VocabularySettings> updateVocabularySettings(VocabularySettingsRequest request) {
        return ResponseEntity.ok(dto(dictionary.setNewCardsPerDay(user.id(), request.getNewCardsPerDay())));
    }

    private static VocabularySettings dto(DictionaryUseCase.SettingsView view) {
        return new VocabularySettings()
                .newCardsPerDay(view.newCardsPerDay())
                .timezone(view.timezone())
                .pronunciationAvailable(view.pronunciationAvailable());
    }
}
