package com.toefly.vocabulary.application.port.out;

import com.toefly.vocabulary.domain.wordset.WordSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** content-service: approved Collins Vocabulary units and the text of an ItemSet. */
public interface ContentCatalogPort {

    List<WordSet> vocabularyUnits();

    /** Stimulus text of the ItemSet (passage, transcript), empty when unknown. */
    Optional<ItemSetText> itemSetText(UUID itemSetId);

    record ItemSetText(String title, String text) {}
}
