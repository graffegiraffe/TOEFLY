import { queryOptions, useMutation, useQueryClient } from '@tanstack/react-query';
import type {
  EntryOrigin,
  Rating,
  ReviewCard,
  ReviewSession,
  VocabularyEntry,
  VocabularyEntryBatch,
  VocabularyStatus,
  WordSetEnrollment,
} from './generated/vocabulary';
import { AddVocabularyEntriesRequestOriginEnum, AddVocabularyEntryRequestOriginEnum } from './generated/vocabulary';
import { api } from './client';
import { newKey } from './practice';

/**
 * Vocabulary API (docs/05-api-contracts.md §6.10): word cards, the personal dictionary, FSRS reviews, Collins word
 * sets and key words of a text. Dictionary data comes from ai-gateway through vocabulary-service only.
 */

export interface EntryFilter {
  q?: string;
  status?: VocabularyStatus;
  origin?: EntryOrigin;
  wordSetId?: string;
  due?: boolean;
  cursor?: string;
}

export const entriesQuery = (filter: EntryFilter) =>
  queryOptions({
    queryKey: ['vocabulary', 'entries', filter],
    queryFn: () => api.dictionary.listVocabularyEntries({ ...filter, limit: 50 }),
    placeholderData: (previous) => previous,
  });

export const entryQuery = (entryId: string) =>
  queryOptions({
    queryKey: ['vocabulary', 'entry', entryId],
    queryFn: () => api.dictionary.getVocabularyEntry({ entryId }),
    retry: false,
  });

export const dueQuery = queryOptions({
  queryKey: ['vocabulary', 'due'],
  queryFn: () => api.reviews.listDueReviewCards(),
});

export const wordSetsQuery = queryOptions({
  queryKey: ['vocabulary', 'word-sets'],
  queryFn: () => api.wordSets.listWordSets(),
  staleTime: 60_000,
});

export const wordSetQuery = (wordSetId: string) =>
  queryOptions({
    queryKey: ['vocabulary', 'word-set', wordSetId],
    queryFn: () => api.wordSets.getWordSet({ wordSetId }),
    retry: false,
  });

/** AC-15.01.1: the card of a selected word; cached per term and sentence. */
export const lookupQuery = (term: string, context?: string) =>
  queryOptions({
    queryKey: ['vocabulary', 'lookup', term.toLowerCase(), context ?? ''],
    queryFn: () => api.dictionary.lookupWord({ term, context }),
    staleTime: 10 * 60_000,
    retry: false,
  });

/** AC-15.02.1: key words of an ItemSet; 503 when ai-gateway is out. */
export const keyWordsQuery = (itemSetId: string) =>
  queryOptions({
    queryKey: ['vocabulary', 'key-words', itemSetId],
    queryFn: () => api.wordSuggestions.suggestWords({ wordSuggestionRequest: { itemSetId } }),
    staleTime: 10 * 60_000,
    retry: false,
  });

export const vocabularySettingsQuery = queryOptions({
  queryKey: ['vocabulary', 'settings'],
  queryFn: () => api.vocabularySettings.getVocabularySettings(),
  staleTime: 5 * 60_000,
});

function useRefreshVocabulary() {
  const client = useQueryClient();
  return () => client.invalidateQueries({ queryKey: ['vocabulary'] });
}

export interface SaveWord {
  term: string;
  context?: string;
  itemSetId?: string;
  sourceLabel?: string;
}

/** AC-15.01.2, AC-15.01.3: "Save word"; `alreadyInDictionary` tells the second save apart. */
export function useSaveWord() {
  const refresh = useRefreshVocabulary();
  return useMutation<VocabularyEntry, Error, SaveWord>({
    mutationFn: (word) =>
      api.dictionary.addVocabularyEntry({
        idempotencyKey: newKey(),
        addVocabularyEntryRequest: {
          term: word.term,
          context: word.context ?? null,
          sourceRef: { itemSetId: word.itemSetId ?? null, label: word.sourceLabel ?? null },
          origin: AddVocabularyEntryRequestOriginEnum.Tap,
        },
      }),
    onSuccess: () => void refresh(),
  });
}

/** AC-15.02.2: "Add all" of the key words. */
export function useSaveWords() {
  const refresh = useRefreshVocabulary();
  return useMutation<
    VocabularyEntryBatch,
    Error,
    { words: { term: string; context?: string | null }[]; itemSetId?: string; sourceLabel?: string }
  >({
    mutationFn: ({ words, itemSetId, sourceLabel }) =>
      api.dictionary.addVocabularyEntries({
        idempotencyKey: newKey(),
        addVocabularyEntriesRequest: {
          words: words.map((word) => ({ term: word.term, context: word.context ?? null })),
          sourceRef: { itemSetId: itemSetId ?? null, label: sourceLabel ?? null },
          origin: AddVocabularyEntriesRequestOriginEnum.KeyWords,
        },
      }),
    onSuccess: () => void refresh(),
  });
}

export function useEditEntry(entry: VocabularyEntry) {
  const client = useQueryClient();
  return useMutation<VocabularyEntry, Error, { translationRu?: string; note?: string; suspended?: boolean }>({
    mutationFn: (patch) =>
      api.dictionary.updateVocabularyEntry({
        entryId: entry.id,
        ifMatch: `"${entry.version}"`,
        vocabularyEntryPatch: patch,
      }),
    onSuccess: (updated) => {
      client.setQueryData(entryQuery(entry.id).queryKey, updated);
      void client.invalidateQueries({ queryKey: ['vocabulary', 'entries'] });
      void client.invalidateQueries({ queryKey: ['vocabulary', 'due'] });
    },
  });
}

export function useDeleteEntry() {
  const refresh = useRefreshVocabulary();
  return useMutation<void, Error, string>({
    mutationFn: (entryId) => api.dictionary.deleteVocabularyEntry({ entryId }),
    onSuccess: () => void refresh(),
  });
}

export function useEnrollWordSet() {
  const refresh = useRefreshVocabulary();
  return useMutation<WordSetEnrollment, Error, string>({
    mutationFn: (wordSetId) => api.wordSets.enrollWordSet({ wordSetId, idempotencyKey: newKey() }),
    onSuccess: () => void refresh(),
  });
}

export function useSetNewCardsPerDay() {
  const refresh = useRefreshVocabulary();
  return useMutation({
    mutationFn: (newCardsPerDay: number) =>
      api.vocabularySettings.updateVocabularySettings({ vocabularySettingsRequest: { newCardsPerDay } }),
    onSuccess: () => void refresh(),
  });
}

export function startReviewSession(weekTaskId?: string): Promise<ReviewSession> {
  return api.reviews.startReviewSession({
    idempotencyKey: newKey(),
    startReviewSessionRequest: { weekTaskId: weekTaskId ?? null },
  });
}

export function gradeCard(
  card: ReviewCard,
  rating: Rating,
  sessionId: string,
  sequenceNo: number,
  elapsedMs: number,
): Promise<ReviewCard> {
  return api.reviews.reviewCard({
    cardId: card.id,
    idempotencyKey: newKey(),
    reviewCardRequest: { rating, reviewSessionId: sessionId, sequenceNo, elapsedMs: Math.min(elapsedMs, 3_600_000) },
  });
}

export function completeReviewSession(sessionId: string): Promise<ReviewSession> {
  return api.reviews.completeReviewSession({ sessionId, idempotencyKey: newKey() });
}

export function useRefreshAfterReview() {
  return useRefreshVocabulary();
}
