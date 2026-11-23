import type { Page } from '@playwright/test';
import { API } from './practice-stub';

/**
 * A vocabulary backend for the vocabulary screens (docs/05-api-contracts.md §6.10): an in-memory dictionary with word
 * cards, FSRS-like grades (fixed intervals), sessions, one Collins unit and key words. It is registered on top of the
 * practice and planning stubs and passes every other request to them. vocabulary-service itself, the FSRS scheduler
 * and ai-gateway are covered by the service's component tests.
 */
export const UNIT_ID = '7d3e1f20-1a2b-4c3d-8e4f-5a6b7c8d9e01';

type Json = Record<string, unknown>;

interface Entry {
  id: string;
  lemma: string;
  data: Json;
  note: string | null;
  contexts: { sentence: string; itemSetId: string | null; sourceLabel: string }[];
  origin: string;
  wordSetId: string | null;
  version: number;
  state: 'NEW' | 'LEARNING' | 'REVIEW';
  stability: number;
  due: string;
  suspended: boolean;
  reps: number;
  createdAt: string;
}

export interface VocabularyStubState {
  saved: Json[];
  grades: { cardId: string; rating: string; sequenceNo: number }[];
  completed: number;
  entries: Map<string, Entry>;
}

const DICTIONARY: Record<string, Json> = {
  campus: {
    partOfSpeech: 'noun',
    definitionEn: 'the buildings and land of a university or college',
    exampleEn: 'She lives on campus in her first year.',
    translationRu: 'кампус, университетская территория',
    ipa: '/ˈkæm.pəs/',
  },
  calcium: {
    partOfSpeech: 'noun',
    definitionEn: 'a soft white element found in bones, shells and coral',
    exampleEn: 'Corals build their skeletons from calcium.',
    translationRu: 'кальций',
    ipa: '/ˈkæl.si.əm/',
  },
  reef: {
    partOfSpeech: 'noun',
    definitionEn: 'a line of rock or coral near the surface of the sea',
    translationRu: 'риф',
    ipa: '/riːf/',
  },
  habitat: {
    partOfSpeech: 'noun',
    definitionEn: 'the natural home of an animal or plant',
    translationRu: 'среда обитания',
  },
  symbiosis: { partOfSpeech: 'noun', definitionEn: 'a close relationship between two species that benefits both' },
};

const KEY_WORDS = [
  {
    lemma: 'campus',
    partOfSpeech: 'noun',
    definitionEn: 'the grounds of a university',
    sentence: 'The campus library closes at 9 pm on Fridays during exam week.',
  },
  {
    lemma: 'exam',
    partOfSpeech: 'noun',
    definitionEn: 'a formal test of knowledge',
    sentence: 'The campus library closes at 9 pm on Fridays during exam week.',
  },
  {
    lemma: 'reef',
    partOfSpeech: 'noun',
    definitionEn: 'a line of coral near the surface',
    sentence: 'Warm water can harm the reefs.',
  },
  {
    lemma: 'calcium',
    partOfSpeech: 'noun',
    definitionEn: 'a soft white element',
    sentence: 'Tiny animals build them from calcium.',
  },
  {
    lemma: 'century',
    partOfSpeech: 'noun',
    definitionEn: 'a period of one hundred years',
    sentence: 'Coral reefs grow slowly over centuries.',
  },
];

const UNIT = {
  id: UNIT_ID,
  unitNo: 1,
  theme: 'LIFE_SCIENCE',
  title: 'Life science: ecosystems',
  sourceLabel: 'Collins Vocabulary Unit 1',
  words: [
    {
      lemma: 'habitat',
      partOfSpeech: 'noun',
      definitionEn: 'the natural home of an animal or plant',
      exampleEn: 'The wetland is a habitat for many birds.',
      ipa: '/ˈhæb.ɪ.tæt/',
    },
    {
      lemma: 'symbiosis',
      partOfSpeech: 'noun',
      definitionEn: 'a close relationship between two species that benefits both',
      exampleEn: null,
      ipa: null,
    },
    {
      lemma: 'reef',
      partOfSpeech: 'noun',
      definitionEn: 'a line of rock or coral near the surface of the sea',
      exampleEn: null,
      ipa: null,
    },
  ],
};

const MINUTE = 60;
const DAY = 86_400;

interface Options {
  /** Entries already in the dictionary when the page opens. */
  seed?: { lemma: string; state?: Entry['state']; stability?: number; source?: string; dueInDays?: number }[];
}

export async function stubVocabulary(page: Page, options: Options = {}): Promise<VocabularyStubState> {
  const state: VocabularyStubState = { saved: [], grades: [], completed: 0, entries: new Map() };
  let counter = 0;
  const now = () => Date.now();
  const id = (prefix: string) => `${prefix}000000-0000-4000-8000-${String(++counter).padStart(12, '0')}`;

  const add = (
    lemma: string,
    context: string | null,
    itemSetId: string | null,
    label: string,
    origin: string,
  ): Entry => {
    const entry: Entry = {
      id: id('e'),
      lemma,
      data: DICTIONARY[lemma] ?? {},
      note: null,
      contexts: context ? [{ sentence: context, itemSetId, sourceLabel: label }] : [],
      origin,
      wordSetId: origin === 'COLLINS_UNIT' ? UNIT_ID : null,
      version: 0,
      state: 'NEW',
      stability: 0,
      due: new Date(now()).toISOString(),
      suspended: false,
      reps: 0,
      createdAt: new Date(now() + counter).toISOString(),
    };
    state.entries.set(entry.id, entry);
    return entry;
  };
  for (const seed of options.seed ?? []) {
    const entry = add(seed.lemma, null, null, seed.source ?? 'Saved word', 'TAP');
    entry.state = seed.state ?? 'NEW';
    entry.stability = seed.stability ?? 0;
    entry.reps = entry.state === 'NEW' ? 0 : 3;
    entry.due = new Date(now() + (seed.dueInDays ?? 0) * DAY * 1000 - 60_000).toISOString();
    if (seed.source)
      entry.contexts = [{ sentence: `We met ${seed.lemma} in a text.`, itemSetId: null, sourceLabel: seed.source }];
  }

  const status = (entry: Entry) =>
    entry.state === 'NEW'
      ? 'NEW'
      : entry.state === 'LEARNING'
        ? 'LEARNING'
        : entry.stability >= 90
          ? 'MASTERED'
          : 'REVIEW';
  const card = (entry: Entry) => ({
    id: entry.id.replace(/^e/, 'c'),
    state: entry.state,
    due: entry.due,
    stability: entry.stability,
    difficulty: 5,
    reps: entry.reps,
    lapses: 0,
    lastReviewAt: null,
    suspended: entry.suspended,
  });
  const wire = (entry: Entry, extra: Json = {}) => ({
    id: entry.id,
    lemma: entry.lemma,
    data: entry.data,
    note: entry.note,
    contexts: entry.contexts,
    origin: entry.origin,
    wordSetId: entry.wordSetId,
    enriched: Boolean(entry.data['definitionEn']),
    status: status(entry),
    card: card(entry),
    createdAt: entry.createdAt,
    updatedAt: entry.createdAt,
    version: entry.version,
    ...extra,
  });
  const byCard = (cardId: string) => [...state.entries.values()].find((entry) => card(entry).id === cardId);
  const intervals = (entry: Entry) =>
    entry.state === 'REVIEW'
      ? { again: 10 * MINUTE, hard: 2 * DAY, good: 5 * DAY, easy: 12 * DAY }
      : { again: MINUTE, hard: 6 * MINUTE, good: 10 * MINUTE, easy: 16 * DAY };
  const formats = ['WORD_TO_DEFINITION', 'DEFINITION_TO_WORD', 'CLOZE'];
  const reviewCard = (entry: Entry, position: number) => {
    const sentence = entry.contexts.at(-1)?.sentence ?? (entry.data['exampleEn'] as string | undefined) ?? null;
    const wanted = formats[position % 3];
    const format =
      wanted === 'CLOZE' && sentence?.toLowerCase().includes(entry.lemma)
        ? 'CLOZE'
        : wanted === 'DEFINITION_TO_WORD' && entry.data['definitionEn']
          ? 'DEFINITION_TO_WORD'
          : 'WORD_TO_DEFINITION';
    return {
      id: card(entry).id,
      entryId: entry.id,
      lemma: entry.lemma,
      data: entry.data,
      contextSentence: sentence,
      format,
      status: status(entry),
      card: card(entry),
      intervals: intervals(entry),
    };
  };
  const dueEntries = () =>
    [...state.entries.values()]
      .filter((entry) => !entry.suspended && Date.parse(entry.due) <= now() + (DAY * 1000) / 2)
      .sort((a, b) => a.createdAt.localeCompare(b.createdAt));
  const dueList = () => {
    const due = dueEntries();
    return {
      localDate: new Date(now()).toISOString().slice(0, 10),
      timezone: 'Europe/Minsk',
      dueCount: due.filter((entry) => entry.state !== 'NEW').length,
      newCount: due.filter((entry) => entry.state === 'NEW').length,
      dueTomorrow: 0,
      items: due.map(reviewCard),
    };
  };
  let session: Json | null = null;

  await page.route(`${API}/**`, async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    const method = request.method();
    const vocabulary =
      /^\/api\/v1\/(word-lookups|vocabulary-entries|vocabulary-entry-batches|review-cards|review-sessions|word-sets|word-suggestions|vocabulary-settings)/.test(
        path,
      );
    if (!vocabulary) return route.fallback();
    const json = (body: unknown, code = 200) =>
      route.fulfill({
        status: code,
        contentType: code >= 400 ? 'application/problem+json' : 'application/json',
        body: body === undefined ? '' : JSON.stringify(body),
        headers: {
          'access-control-allow-origin': request.headers()['origin'] ?? '*',
          'access-control-allow-credentials': 'true',
          'access-control-allow-headers': '*',
          'access-control-allow-methods': '*',
          'access-control-expose-headers': 'ETag',
        },
      });
    if (method === 'OPTIONS') return json({});
    const body = () => (request.postDataJSON() ?? {}) as Json;
    const find = (lemma: string) => [...state.entries.values()].find((entry) => entry.lemma === lemma);

    if (path === '/api/v1/word-lookups') {
      const term = (url.searchParams.get('term') ?? '').toLowerCase();
      const lemma = term.endsWith('s') && DICTIONARY[term.slice(0, -1)] ? term.slice(0, -1) : term;
      return json({
        lemma,
        data: DICTIONARY[lemma] ?? {},
        available: Boolean(DICTIONARY[lemma]),
        entryId: find(lemma)?.id ?? null,
        pronunciationAvailable: false,
      });
    }
    if (path === '/api/v1/vocabulary-entries' && method === 'POST') {
      const request_ = body();
      state.saved.push(request_);
      const lemma = String(request_['term']).toLowerCase();
      const source = (request_['sourceRef'] ?? {}) as Json;
      const existing = find(lemma);
      if (existing) {
        if (request_['context']) {
          existing.contexts.push({
            sentence: String(request_['context']),
            itemSetId: null,
            sourceLabel: String(source['label'] ?? 'Saved word'),
          });
        }
        return json(wire(existing, { alreadyInDictionary: true }));
      }
      const entry = add(
        lemma,
        (request_['context'] as string) ?? null,
        (source['itemSetId'] as string) ?? null,
        String(source['label'] ?? 'Saved word'),
        String(request_['origin'] ?? 'TAP'),
      );
      return json(wire(entry, { alreadyInDictionary: false }), 201);
    }
    if (path === '/api/v1/vocabulary-entry-batches') {
      const request_ = body();
      const source = (request_['sourceRef'] ?? {}) as Json;
      let added = 0;
      const items = (request_['words'] as Json[]).map((word) => {
        const lemma = String(word['term']).toLowerCase();
        const existing = find(lemma);
        if (existing) return wire(existing, { alreadyInDictionary: true });
        added++;
        return wire(
          add(
            lemma,
            (word['context'] as string) ?? null,
            (source['itemSetId'] as string) ?? null,
            String(source['label'] ?? 'Key words'),
            'KEY_WORDS',
          ),
          { alreadyInDictionary: false },
        );
      });
      return json({ added, alreadyInDictionary: items.length - added, items });
    }
    if (path === '/api/v1/vocabulary-entries' && method === 'GET') {
      const q = url.searchParams.get('q')?.toLowerCase();
      const wanted = url.searchParams.get('status');
      const origin = url.searchParams.get('origin');
      const due = url.searchParams.get('due') === 'true';
      const items = [...state.entries.values()]
        .filter((entry) => !q || entry.lemma.startsWith(q))
        .filter((entry) => !wanted || status(entry) === wanted)
        .filter((entry) => !origin || entry.origin === origin)
        .filter((entry) => !due || (entry.state !== 'NEW' && Date.parse(entry.due) <= now() + (DAY * 1000) / 2))
        .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
        .map((entry) => wire(entry));
      return json({ items, page: { nextCursor: null, hasMore: false, limit: 50 }, total: items.length });
    }
    const entryPath = path.match(/^\/api\/v1\/vocabulary-entries\/([^/]+)$/);
    if (entryPath) {
      const entry = state.entries.get(entryPath[1] ?? '');
      if (!entry) return json({ title: 'Not found', status: 404, code: 'NOT_FOUND' }, 404);
      if (method === 'GET') return json(wire(entry));
      if (method === 'DELETE') {
        state.entries.delete(entry.id);
        return json(undefined, 204);
      }
      if (method === 'PATCH') {
        if (request.headers()['if-match'] !== `"${entry.version}"`) {
          return json({ title: 'Precondition failed', status: 412, code: 'PRECONDITION_FAILED' }, 412);
        }
        const patch = body();
        if (typeof patch['translationRu'] === 'string')
          entry.data = { ...entry.data, translationRu: patch['translationRu'] || null };
        if (typeof patch['note'] === 'string') entry.note = patch['note'] || null;
        if (typeof patch['suspended'] === 'boolean') entry.suspended = patch['suspended'];
        entry.version++;
        return json(wire(entry));
      }
    }
    if (path === '/api/v1/review-cards/due') return json(dueList());
    if (path === '/api/v1/review-sessions' && method === 'POST') {
      const due = dueEntries();
      session = {
        id: 'ses00000-0000-4000-8000-000000000001',
        weekTaskId: null,
        status: 'IN_PROGRESS',
        localDate: new Date(now()).toISOString().slice(0, 10),
        timezone: 'Europe/Minsk',
        planned: due.length,
        reviewed: 0,
        againCount: 0,
        newIntroduced: 0,
        activeSeconds: 0,
        startedAt: new Date(now()).toISOString(),
        cards: due.map(reviewCard),
      };
      return json(session, 201);
    }
    const graded = path.match(/^\/api\/v1\/review-cards\/([^/]+)\/reviews$/);
    if (graded) {
      const entry = byCard(graded[1] ?? '');
      if (!entry) return json({ title: 'Not found', status: 404, code: 'NOT_FOUND' }, 404);
      const grade = body();
      state.grades.push({
        cardId: graded[1] ?? '',
        rating: String(grade['rating']),
        sequenceNo: Number(grade['sequenceNo']),
      });
      const seconds = intervals(entry)[String(grade['rating']).toLowerCase() as 'again'];
      entry.state =
        grade['rating'] === 'AGAIN' || (entry.state !== 'REVIEW' && grade['rating'] !== 'EASY') ? 'LEARNING' : 'REVIEW';
      if (entry.state === 'REVIEW') entry.stability = Math.max(entry.stability, seconds / DAY);
      entry.reps++;
      entry.due = new Date(now() + seconds * 1000).toISOString();
      return json(reviewCard(entry, 0));
    }
    if (path.endsWith('/completion') && session) {
      state.completed++;
      const reviewed = new Set(state.grades.map((grade) => grade.cardId)).size;
      return json({
        ...session,
        status: 'COMPLETED',
        reviewed,
        completedAt: new Date(now()).toISOString(),
        dueRemaining: 0,
        dueTomorrow: 0,
        nextDueInSeconds: 6 * 3600,
        cards: [],
      });
    }
    if (path === '/api/v1/word-sets') {
      const inDictionary = UNIT.words.filter((word) => find(word.lemma)).length;
      return json({ items: [{ ...UNIT, words: undefined, wordsTotal: UNIT.words.length, inDictionary, learned: 0 }] });
    }
    if (path === `/api/v1/word-sets/${UNIT_ID}`) {
      const inDictionary = UNIT.words.filter((word) => find(word.lemma)).length;
      return json({ ...UNIT, wordsTotal: UNIT.words.length, inDictionary, learned: 0 });
    }
    if (path === `/api/v1/word-sets/${UNIT_ID}/enrollment`) {
      let added = 0;
      for (const word of UNIT.words) {
        if (!find(word.lemma)) {
          add(word.lemma, word.exampleEn, null, UNIT.sourceLabel, 'COLLINS_UNIT');
          added++;
        }
      }
      return json({ wordSetId: UNIT_ID, added, alreadyInDictionary: UNIT.words.length - added });
    }
    if (path === '/api/v1/word-suggestions') {
      return json({ items: KEY_WORDS.filter((word) => !find(word.lemma)) });
    }
    if (path === '/api/v1/vocabulary-settings') {
      return json({ newCardsPerDay: 10, timezone: 'Europe/Minsk', pronunciationAvailable: false });
    }
    return json({ title: `not stubbed: ${method} ${path}` }, 404);
  });

  return state;
}
