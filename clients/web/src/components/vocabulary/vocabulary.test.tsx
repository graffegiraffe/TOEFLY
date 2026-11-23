import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactNode } from 'react';
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import '@/i18n';
import {
  CardFormat,
  CardState,
  Rating,
  ReviewSessionStatusEnum,
  VocabularyStatus,
  type ReviewCard,
  type ReviewSession,
} from '@/api/generated/vocabulary';

const lookupWord = vi.fn();
const addVocabularyEntry = vi.fn();
const addVocabularyEntries = vi.fn();
const suggestWords = vi.fn();
vi.mock('@/api/client', () => ({
  api: {
    dictionary: {
      lookupWord: (...a: unknown[]) => lookupWord(...a),
      addVocabularyEntry: (...a: unknown[]) => addVocabularyEntry(...a),
      addVocabularyEntries: (...a: unknown[]) => addVocabularyEntries(...a),
    },
    wordSuggestions: { suggestWords: (...a: unknown[]) => suggestWords(...a) },
  },
}));
vi.mock('@tanstack/react-router', () => ({
  Link: ({ children, to }: { children: ReactNode; to: string }) => <a href={to}>{children}</a>,
  useNavigate: () => vi.fn(),
}));

import { KeyWords } from './key-words';
import { ReviewSessionView, type ReviewSessionViewProps } from './review-session';
import { StabilityDots } from './stability-dots';
import { WordLookupArea } from './word-lookup';

function wrap(node: ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={client}>{node}</QueryClientProvider>);
}

const T0 = Date.parse('2026-11-23T10:00:00Z');

function card(id: string, lemma: string, format: CardFormat = CardFormat.WordToDefinition): ReviewCard {
  return {
    id,
    entryId: `e-${id}`,
    lemma,
    data: { partOfSpeech: 'noun', definitionEn: `meaning of ${lemma}`, translationRu: 'перевод', ipa: '/x/' },
    contextSentence: `A dense ${lemma} shades the streets.`,
    format,
    status: VocabularyStatus.New,
    card: {
      id,
      state: CardState.New,
      due: new Date(T0),
      stability: 0,
      difficulty: 0,
      reps: 0,
      lapses: 0,
      suspended: false,
    },
    intervals: { again: 60, hard: 330, good: 600, easy: 16 * 86_400 },
  };
}

function session(cards: ReviewCard[]): ReviewSession {
  return {
    id: 's1',
    status: ReviewSessionStatusEnum.InProgress,
    localDate: new Date('2026-11-23'),
    timezone: 'Europe/Minsk',
    planned: cards.length,
    reviewed: 0,
    againCount: 0,
    newIntroduced: 0,
    startedAt: new Date(T0),
    cards,
  };
}

function graded(source: ReviewCard, state: CardState, dueInSeconds: number): ReviewCard {
  return { ...source, card: { ...source.card, state, due: new Date(T0 + dueInSeconds * 1000) } };
}

describe('US-15.03 FSRS review session', () => {
  it('AC-15.03.2, AC-15.03.5: Space shows the answer, keys grade, Again comes back, the summary closes it', async () => {
    const user = userEvent.setup();
    const canopy = card('c1', 'canopy');
    const habitat = card('c2', 'habitat', CardFormat.DefinitionToWord);
    const grade = vi.fn<ReviewSessionViewProps['grade']>(async (graded_, rating) =>
      rating === Rating.Again ? graded(graded_, CardState.Learning, 60) : graded(graded_, CardState.Review, 3 * 86_400),
    );
    const complete = vi.fn(async () => ({
      ...session([]),
      status: ReviewSessionStatusEnum.Completed,
      planned: 2,
      reviewed: 2,
      nextDueInSeconds: 6 * 3600,
    }));
    const now = () => T0;
    wrap(
      <ReviewSessionView
        session={session([canopy, habitat])}
        grade={grade}
        complete={complete}
        onDone={vi.fn()}
        now={now}
      />,
    );

    expect(screen.getByTestId('card-front')).toHaveTextContent('canopy');
    expect(screen.getByTestId('review-progress')).toHaveTextContent('1 of 2');
    expect(screen.queryByTestId('card-back')).toBeNull();

    await user.keyboard(' ');
    expect(screen.getByTestId('card-back')).toHaveTextContent('meaning of canopy');
    expect(screen.getByRole('button', { name: /Again\s*1m/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Hard\s*6m/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Good\s*10m/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Easy\s*16d/ })).toBeInTheDocument();

    await user.keyboard('1');
    await waitFor(() => expect(grade).toHaveBeenCalledTimes(1));
    expect(grade.mock.calls[0]?.[1]).toBe(Rating.Again);
    expect(grade.mock.calls[0]?.[2]).toBe(0);

    // the definition → word format of the second card
    await waitFor(() => expect(screen.getByTestId('card-front')).toHaveTextContent('meaning of habitat'));
    await user.click(screen.getByRole('button', { name: 'Show answer' }));
    await user.click(screen.getByRole('button', { name: /Good/ }));

    // canopy returns after Again
    await waitFor(() => expect(screen.getByTestId('card-front')).toHaveTextContent('canopy'));
    await user.keyboard(' ');
    await user.keyboard('3');

    await waitFor(() => expect(screen.getByTestId('review-summary')).toHaveTextContent('2 reviewed · next in 6 h'));
    expect(grade).toHaveBeenCalledTimes(3);
    expect(grade.mock.calls.map((call) => call[2])).toEqual([0, 1, 2]);
    expect(complete).toHaveBeenCalledTimes(1);
  });

  it('AC-15.03.2: the cloze format hides the word in its sentence', () => {
    wrap(
      <ReviewSessionView
        session={session([card('c3', 'canopy', CardFormat.Cloze)])}
        grade={vi.fn()}
        complete={vi.fn()}
        onDone={vi.fn()}
      />,
    );
    const front = screen.getByTestId('card-front');
    expect(front).toHaveTextContent('A dense shades the streets.');
    expect(front).not.toHaveTextContent('canopy');
  });

  it('AC-15.03.4: a completed empty session says there is nothing to review', () => {
    wrap(
      <ReviewSessionView
        session={{ ...session([]), status: ReviewSessionStatusEnum.Completed }}
        grade={vi.fn()}
        complete={vi.fn()}
        onDone={vi.fn()}
      />,
    );
    expect(screen.getByRole('heading', { name: 'Nothing to review now' })).toBeInTheDocument();
  });

  it('a failed grade keeps the card and tells the learner', async () => {
    const user = userEvent.setup();
    const grade = vi.fn().mockRejectedValue(new Error('offline'));
    wrap(
      <ReviewSessionView session={session([card('c4', 'canopy')])} grade={grade} complete={vi.fn()} onDone={vi.fn()} />,
    );
    await user.click(screen.getByRole('button', { name: 'Show answer' }));
    await user.click(screen.getByRole('button', { name: /Easy/ }));
    expect(await screen.findByRole('alert')).toHaveTextContent('The answer was not saved');
    expect(screen.getByTestId('card-front')).toHaveTextContent('canopy');
  });
});

describe('US-15.01 saving a word from a text', () => {
  beforeAll(() => {
    Range.prototype.getBoundingClientRect = () => new DOMRect(40, 40, 60, 20);
  });

  beforeEach(() => {
    lookupWord.mockReset();
    addVocabularyEntry.mockReset();
  });

  function select(text: string) {
    const paragraph = screen.getByText(/dense canopy/);
    const node = paragraph.firstChild as Text;
    const start = node.data.indexOf(text);
    const range = document.createRange();
    range.setStart(node, start);
    range.setEnd(node, start + text.length);
    const selection = window.getSelection();
    selection?.removeAllRanges();
    selection?.addRange(range);
    fireEvent.mouseUp(paragraph);
  }

  const passage = (
    <p>Urban forests mitigate the heat island effect. A dense canopy shades the streets. Leaves cool the air.</p>
  );

  it('AC-15.01.1, AC-15.01.2: the card shows the dictionary data and saves the word with its sentence', async () => {
    const user = userEvent.setup();
    lookupWord.mockResolvedValue({
      lemma: 'canopy',
      data: {
        partOfSpeech: 'noun',
        definitionEn: 'the upper layer of leaves',
        translationRu: 'полог',
        ipa: '/ˈkæn.ə.pi/',
      },
      available: true,
      entryId: null,
      pronunciationAvailable: false,
    });
    addVocabularyEntry.mockResolvedValue({ id: 'e1', alreadyInDictionary: false });
    wrap(
      <WordLookupArea itemSetId="set-1" sourceLabel="Urban forests">
        {passage}
      </WordLookupArea>,
    );
    act(() => select('canopy'));

    const dialog = await screen.findByRole('dialog', { name: 'canopy' });
    expect(lookupWord).toHaveBeenCalledWith({ term: 'canopy', context: 'A dense canopy shades the streets.' });
    expect(await screen.findByText('the upper layer of leaves')).toBeInTheDocument();
    expect(dialog).toHaveTextContent('полог');
    expect(dialog).toHaveTextContent('noun · /ˈkæn.ə.pi/');

    await user.click(screen.getByRole('button', { name: 'Save word' }));
    expect(await screen.findByRole('status')).toHaveTextContent('Saved to your dictionary');
    const request = addVocabularyEntry.mock.calls[0]?.[0] as { addVocabularyEntryRequest: Record<string, unknown> };
    expect(request.addVocabularyEntryRequest).toMatchObject({
      term: 'canopy',
      context: 'A dense canopy shades the streets.',
      sourceRef: { itemSetId: 'set-1', label: 'Urban forests' },
    });

    await user.keyboard('{Escape}');
    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it('AC-15.01.3: a word already in the dictionary says so', async () => {
    lookupWord.mockResolvedValue({
      lemma: 'canopy',
      data: {},
      available: false,
      entryId: 'e1',
      pronunciationAvailable: false,
    });
    wrap(<WordLookupArea>{passage}</WordLookupArea>);
    act(() => select('canopy'));
    expect(await screen.findByRole('status')).toHaveTextContent('Already in your dictionary');
    expect(screen.getByText('No definition yet. You can still save the word.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Save word' })).toBeNull();
  });

  it('AC-15.01.4: in Exam mode no card appears; long selections are ignored', () => {
    const { unmount } = wrap(<WordLookupArea enabled={false}>{passage}</WordLookupArea>);
    act(() => select('canopy'));
    expect(screen.queryByRole('dialog')).toBeNull();
    unmount();

    wrap(<WordLookupArea>{passage}</WordLookupArea>);
    act(() => select('A dense canopy shades the'));
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(lookupWord).not.toHaveBeenCalled();
  });
});

describe('US-15.02 key words', () => {
  beforeEach(() => {
    suggestWords.mockReset();
    addVocabularyEntries.mockReset();
  });

  it('AC-15.02.1, AC-15.02.2: all words are ticked and added in one action', async () => {
    const user = userEvent.setup();
    suggestWords.mockResolvedValue({
      items: [
        { lemma: 'canopy', partOfSpeech: 'noun', definitionEn: 'upper layer', sentence: 'A dense canopy.' },
        { lemma: 'allocate', partOfSpeech: 'verb', definitionEn: 'give for a purpose', sentence: 'Cities allocate.' },
        { lemma: 'substantial', partOfSpeech: 'adjective', definitionEn: 'large', sentence: null },
      ],
    });
    addVocabularyEntries.mockResolvedValue({ added: 2, alreadyInDictionary: 0, items: [] });
    wrap(<KeyWords itemSetId="set-1" sourceLabel="Urban forests" />);

    expect(await screen.findByRole('button', { name: 'Add 3 words' })).toBeInTheDocument();
    await user.click(screen.getByRole('checkbox', { name: /substantial/ }));
    await user.click(screen.getByRole('button', { name: 'Add 2 words' }));

    expect(await screen.findByRole('status')).toHaveTextContent('Added 2 words');
    const request = addVocabularyEntries.mock.calls[0]?.[0] as { addVocabularyEntriesRequest: Record<string, unknown> };
    expect(request.addVocabularyEntriesRequest).toMatchObject({
      words: [
        { term: 'canopy', context: 'A dense canopy.' },
        { term: 'allocate', context: 'Cities allocate.' },
      ],
      sourceRef: { itemSetId: 'set-1', label: 'Urban forests' },
      origin: 'KEY_WORDS',
    });
  });

  it('AC-15.02.1: without ai-gateway the block explains that suggestions are unavailable', async () => {
    suggestWords.mockRejectedValue(new Error('503'));
    wrap(<KeyWords itemSetId="set-1" />);
    expect(await screen.findByText('Word suggestions are not available right now.')).toBeInTheDocument();
  });
});

describe('docs/07 §5.14 stability indicator', () => {
  it('names the level for screen readers', () => {
    render(<StabilityDots level={3} />);
    expect(screen.getByRole('img', { name: 'Memory strength 3 of 4' })).toBeInTheDocument();
  });
});
