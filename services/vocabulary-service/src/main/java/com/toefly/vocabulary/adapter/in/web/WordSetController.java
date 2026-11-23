package com.toefly.vocabulary.adapter.in.web;

import com.toefly.vocabulary.adapter.in.web.api.WordSetsApi;
import com.toefly.vocabulary.adapter.in.web.dto.WordSet;
import com.toefly.vocabulary.adapter.in.web.dto.WordSetEnrollment;
import com.toefly.vocabulary.adapter.in.web.dto.WordSetList;
import com.toefly.vocabulary.application.port.in.WordSetUseCase;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Collins Vocabulary units (US-15.04). */
@RestController
public class WordSetController implements WordSetsApi {

    private final WordSetUseCase wordSets;
    private final CurrentUser user;
    private final Idempotent idempotent;

    public WordSetController(WordSetUseCase wordSets, CurrentUser user, Idempotent idempotent) {
        this.wordSets = wordSets;
        this.user = user;
        this.idempotent = idempotent;
    }

    @Override
    public ResponseEntity<WordSetList> listWordSets() {
        return ResponseEntity.ok(new WordSetList()
                .items(wordSets.list(user.id()).stream()
                        .map(VocabularyDtoMapper::wordSetSummary)
                        .toList()));
    }

    @Override
    public ResponseEntity<WordSet> getWordSet(UUID wordSetId) {
        return ResponseEntity.ok(VocabularyDtoMapper.wordSet(wordSets.get(user.id(), wordSetId)));
    }

    @Override
    public ResponseEntity<WordSetEnrollment> enrollWordSet(String idempotencyKey, UUID wordSetId) {
        UUID userId = user.id();
        return idempotent.run(
                userId,
                idempotencyKey,
                "enrollWordSet:" + wordSetId,
                null,
                WordSetEnrollment.class,
                HttpStatus.OK,
                () -> {
                    WordSetUseCase.Enrollment enrollment = wordSets.enroll(userId, wordSetId);
                    return new WordSetEnrollment()
                            .wordSetId(enrollment.wordSetId())
                            .added(enrollment.added())
                            .alreadyInDictionary(enrollment.alreadyPresent());
                });
    }
}
