package com.toefly.vocabulary.adapter.in.web;

import com.toefly.common.web.CursorCodec;
import com.toefly.vocabulary.adapter.in.web.api.DictionaryApi;
import com.toefly.vocabulary.adapter.in.web.dto.AddVocabularyEntriesRequest;
import com.toefly.vocabulary.adapter.in.web.dto.AddVocabularyEntriesRequestWordsInner;
import com.toefly.vocabulary.adapter.in.web.dto.AddVocabularyEntryRequest;
import com.toefly.vocabulary.adapter.in.web.dto.EntryOrigin;
import com.toefly.vocabulary.adapter.in.web.dto.PageInfo;
import com.toefly.vocabulary.adapter.in.web.dto.SourceRef;
import com.toefly.vocabulary.adapter.in.web.dto.VocabularyEntry;
import com.toefly.vocabulary.adapter.in.web.dto.VocabularyEntryBatch;
import com.toefly.vocabulary.adapter.in.web.dto.VocabularyEntryPage;
import com.toefly.vocabulary.adapter.in.web.dto.VocabularyEntryPatch;
import com.toefly.vocabulary.adapter.in.web.dto.VocabularyStatus;
import com.toefly.vocabulary.adapter.in.web.dto.WordCard;
import com.toefly.vocabulary.application.port.in.AddWordCommand;
import com.toefly.vocabulary.application.port.in.AddWordResult;
import com.toefly.vocabulary.application.port.in.DictionaryUseCase;
import com.toefly.vocabulary.application.port.in.EntryPage;
import com.toefly.vocabulary.application.port.in.EntryQuery;
import com.toefly.vocabulary.application.port.in.EntryRow;
import com.toefly.vocabulary.application.port.in.VocabularyException;
import com.toefly.vocabulary.application.port.in.WordCardView;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Word card, saving words and "My words" (US-15.01, US-15.06). */
@RestController
public class DictionaryController implements DictionaryApi {

    private final DictionaryUseCase dictionary;
    private final CurrentUser user;
    private final Idempotent idempotent;

    public DictionaryController(DictionaryUseCase dictionary, CurrentUser user, Idempotent idempotent) {
        this.dictionary = dictionary;
        this.user = user;
        this.idempotent = idempotent;
    }

    @Override
    public ResponseEntity<WordCard> lookupWord(String term, @Nullable String context) {
        WordCardView card = dictionary.lookup(user.id(), term, context);
        return ResponseEntity.ok(new WordCard()
                .lemma(card.lemma())
                .data(VocabularyDtoMapper.data(card.details()))
                .available(card.available())
                .entryId(card.entryId())
                .pronunciationAvailable(card.pronunciation()));
    }

    @Override
    public ResponseEntity<VocabularyEntry> addVocabularyEntry(
            String idempotencyKey, AddVocabularyEntryRequest request) {
        UUID userId = user.id();
        return idempotent.run(userId, idempotencyKey, "addVocabularyEntry", request, VocabularyEntry.class, () -> {
            SourceRef source = request.getSourceRef();
            AddWordResult result = dictionary.add(new AddWordCommand(
                    userId,
                    request.getTerm(),
                    request.getContext(),
                    source == null ? null : source.getItemSetId(),
                    source == null ? null : source.getLabel(),
                    com.toefly.vocabulary.domain.entry.EntryOrigin.valueOf(
                            request.getOrigin().getValue())));
            return saved(result);
        });
    }

    @Override
    public ResponseEntity<VocabularyEntryBatch> addVocabularyEntries(
            String idempotencyKey, AddVocabularyEntriesRequest request) {
        UUID userId = user.id();
        return idempotent.run(
                userId,
                idempotencyKey,
                "addVocabularyEntries",
                request,
                VocabularyEntryBatch.class,
                HttpStatus.OK,
                () -> {
                    SourceRef source = request.getSourceRef();
                    List<AddWordCommand> commands = new ArrayList<>();
                    for (AddVocabularyEntriesRequestWordsInner word : request.getWords()) {
                        commands.add(new AddWordCommand(
                                userId,
                                word.getTerm(),
                                word.getContext(),
                                source == null ? null : source.getItemSetId(),
                                source == null ? null : source.getLabel(),
                                com.toefly.vocabulary.domain.entry.EntryOrigin.valueOf(
                                        request.getOrigin().getValue())));
                    }
                    List<AddWordResult> results = dictionary.addAll(commands);
                    int added = (int)
                            results.stream().filter(AddWordResult::created).count();
                    return new VocabularyEntryBatch()
                            .added(added)
                            .alreadyInDictionary(results.size() - added)
                            .items(results.stream()
                                    .map(result -> VocabularyDtoMapper.entry(result.row())
                                            .alreadyInDictionary(!result.created()))
                                    .toList());
                });
    }

    @Override
    public ResponseEntity<VocabularyEntryPage> listVocabularyEntries(
            @Nullable String q,
            @Nullable VocabularyStatus status,
            @Nullable EntryOrigin origin,
            @Nullable UUID wordSetId,
            @Nullable Boolean due,
            String sort,
            Integer limit,
            @Nullable String cursor) {
        int size = CursorCodec.limit(limit);
        String fingerprint = String.join(
                "|",
                String.valueOf(q),
                String.valueOf(status),
                String.valueOf(origin),
                String.valueOf(wordSetId),
                String.valueOf(due),
                sort);
        int offset = 0;
        if (cursor != null && !cursor.isBlank()) {
            offset = parseOffset(CursorCodec.decode(cursor, fingerprint));
        }
        EntryQuery.Sort order;
        try {
            order = EntryQuery.Sort.valueOf(sort);
        } catch (IllegalArgumentException unknown) {
            throw VocabularyException.invalid("Unknown sort " + sort + ".");
        }
        EntryPage page = dictionary.list(
                user.id(),
                new EntryQuery(
                        q == null || q.isBlank() ? null : q.strip(),
                        status == null
                                ? null
                                : com.toefly.vocabulary.domain.card.VocabularyStatus.valueOf(status.name()),
                        origin == null ? null : com.toefly.vocabulary.domain.entry.EntryOrigin.valueOf(origin.name()),
                        wordSetId,
                        Boolean.TRUE.equals(due),
                        order,
                        offset,
                        size));
        String next = page.hasMore() ? CursorCodec.encode(fingerprint, List.of(String.valueOf(offset + size))) : null;
        return ResponseEntity.ok(new VocabularyEntryPage()
                .items(page.items().stream().map(VocabularyDtoMapper::entry).toList())
                .page(new PageInfo().nextCursor(next).hasMore(page.hasMore()).limit(size))
                .total((int) page.total()));
    }

    @Override
    public ResponseEntity<VocabularyEntry> getVocabularyEntry(UUID entryId) {
        EntryRow row = dictionary.get(user.id(), entryId);
        return ResponseEntity.ok()
                .eTag(VocabularyDtoMapper.etag(row.entry().version()))
                .body(VocabularyDtoMapper.entry(row));
    }

    @Override
    public ResponseEntity<VocabularyEntry> updateVocabularyEntry(
            String ifMatch, UUID entryId, VocabularyEntryPatch patch) {
        EntryRow row = dictionary.edit(
                user.id(),
                entryId,
                version(ifMatch),
                new DictionaryUseCase.EntryEdit(patch.getTranslationRu(), patch.getNote(), patch.getSuspended()));
        return ResponseEntity.ok()
                .eTag(VocabularyDtoMapper.etag(row.entry().version()))
                .body(VocabularyDtoMapper.entry(row));
    }

    @Override
    public ResponseEntity<Void> deleteVocabularyEntry(UUID entryId) {
        dictionary.delete(user.id(), entryId);
        return ResponseEntity.noContent().build();
    }

    static ResponseEntity<VocabularyEntry> saved(AddWordResult result) {
        VocabularyEntry body = VocabularyDtoMapper.entry(result.row()).alreadyInDictionary(!result.created());
        if (!result.created()) {
            return ResponseEntity.ok(body);
        }
        return ResponseEntity.created(URI.create(
                        "/api/v1/vocabulary-entries/" + result.row().entry().id()))
                .eTag(VocabularyDtoMapper.etag(result.row().entry().version()))
                .body(body);
    }

    private static int version(String ifMatch) {
        String value = ifMatch.strip();
        if (value.startsWith("W/")) {
            value = value.substring(2);
        }
        value = value.replace("\"", "");
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException malformed) {
            throw VocabularyException.preconditionFailed();
        }
    }

    private static int parseOffset(List<String> keys) {
        try {
            int offset = Integer.parseInt(keys.getFirst());
            if (offset < 0) {
                throw new NumberFormatException();
            }
            return offset;
        } catch (RuntimeException malformed) {
            throw new com.toefly.common.web.InvalidCursorException();
        }
    }
}
