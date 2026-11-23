package com.toefly.vocabulary.adapter.in.web;

import com.toefly.vocabulary.adapter.in.web.api.SuggestionsApi;
import com.toefly.vocabulary.adapter.in.web.dto.WordSuggestion;
import com.toefly.vocabulary.adapter.in.web.dto.WordSuggestionList;
import com.toefly.vocabulary.adapter.in.web.dto.WordSuggestionRequest;
import com.toefly.vocabulary.application.port.in.SuggestionUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** "Key words" of a text (US-15.02). */
@RestController
public class SuggestionController implements SuggestionsApi {

    private final SuggestionUseCase suggestions;
    private final CurrentUser user;

    public SuggestionController(SuggestionUseCase suggestions, CurrentUser user) {
        this.suggestions = suggestions;
        this.user = user;
    }

    @Override
    public ResponseEntity<WordSuggestionList> suggestWords(WordSuggestionRequest request) {
        return ResponseEntity.ok(new WordSuggestionList()
                .items(suggestions.suggest(user.id(), request.getItemSetId(), request.getText()).stream()
                        .map(word -> new WordSuggestion()
                                .lemma(word.lemma())
                                .partOfSpeech(word.partOfSpeech())
                                .definitionEn(word.definitionEn())
                                .sentence(word.sentence()))
                        .toList()));
    }
}
