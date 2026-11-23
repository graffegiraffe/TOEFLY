package com.toefly.vocabulary.application.port.in;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** The personal dictionary (US-15.01, US-15.06). */
public interface DictionaryUseCase {

    WordCardView lookup(UUID userId, String term, @Nullable String context);

    AddWordResult add(AddWordCommand command);

    /** AC-15.02.2: "Add all" — words already in the dictionary only get the context. */
    List<AddWordResult> addAll(List<AddWordCommand> commands);

    EntryRow get(UUID userId, UUID entryId);

    EntryPage list(UUID userId, EntryQuery query);

    EntryRow edit(UUID userId, UUID entryId, int expectedVersion, EntryEdit edit);

    void delete(UUID userId, UUID entryId);

    SettingsView settings(UUID userId);

    SettingsView setNewCardsPerDay(UUID userId, int newCardsPerDay);

    /** Settings of the dictionary: new cards per day, the zone of the day and the pronunciation switch. */
    record SettingsView(int newCardsPerDay, String timezone, boolean pronunciationAvailable) {}

    /** "Edit" fields; a null field is left as is. */
    record EntryEdit(
            @Nullable String translationRu,
            @Nullable String note,
            @Nullable Boolean suspended) {}
}
