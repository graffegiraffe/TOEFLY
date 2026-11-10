import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import type { Page, Route } from '@playwright/test';

/**
 * A practice backend for the player scenarios. It serves the item-set fixtures of practice-service (our own texts,
 * the same ones its component tests use), hides the keys the way the service does and checks answers against the
 * fixture answers, so the screens are exercised with only `pnpm dev` running.
 */
export const API = 'http://api.toefly.localhost:8080';
export const AUDIO_HOST = 'https://audio.toefly.test';

const FIXTURES = fileURLToPath(
  new URL('../../../services/practice-service/src/test/resources/fixtures/item-sets/', import.meta.url),
);

const SECRET = new Set([
  'answerKey',
  'answerKeys',
  'acceptedAnswers',
  'acceptedKeys',
  'explanation',
  'rationale',
  'transcript',
  'fullWord',
  'suffix',
  'modelAnswer',
  'modelAnswers',
  'distractorChunkId',
  'errorKey',
  'correction',
  'keyExplanation',
]);

type Json = Record<string, unknown>;

interface Fixture {
  id: string;
  taskType: string;
  section: string | null;
  topic: string;
  itemCount: number;
  estimatedMinutes: number;
  etsNormSeconds: number;
  payload: { stimulus: Json; items: Json[] };
  _answers: Json[];
}

export function fixture(taskType: string): Fixture {
  return JSON.parse(readFileSync(`${FIXTURES}${taskType}.json`, 'utf8')) as Fixture;
}

function redact(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(redact);
  if (value && typeof value === 'object') {
    const out: Json = {};
    for (const [key, inner] of Object.entries(value as Json)) {
      if (SECRET.has(key)) continue;
      out[key] =
        key === 'markup' && typeof inner === 'string' ? inner.replace(/(\w*)\{[^}]*\}/g, '$1{_}') : redact(inner);
    }
    return out;
  }
  return value;
}

/** Points the audio of a listening fixture to the stub host, which serves a short silent clip. */
function withAudio(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(withAudio);
  if (value && typeof value === 'object') {
    const out: Json = {};
    for (const [key, inner] of Object.entries(value as Json)) {
      out[key] =
        key === 'audio' && inner && typeof inner === 'object'
          ? { ...(inner as Json), url: `${AUDIO_HOST}/clip.wav` }
          : withAudio(inner);
    }
    return out;
  }
  return value;
}

function silentWav(seconds = 0.4, rate = 8000): Buffer {
  const samples = Math.round(seconds * rate);
  const buffer = Buffer.alloc(44 + samples * 2);
  buffer.write('RIFF', 0);
  buffer.writeUInt32LE(36 + samples * 2, 4);
  buffer.write('WAVEfmt ', 8);
  buffer.writeUInt32LE(16, 16);
  buffer.writeUInt16LE(1, 20);
  buffer.writeUInt16LE(1, 22);
  buffer.writeUInt32LE(rate, 24);
  buffer.writeUInt32LE(rate * 2, 28);
  buffer.writeUInt16LE(2, 32);
  buffer.writeUInt16LE(16, 34);
  buffer.write('data', 36);
  buffer.writeUInt32LE(samples * 2, 40);
  return buffer;
}

function normalize(answer: unknown): string {
  const value = answer as Json | null;
  if (!value) return 'null';
  if (value.type === 'TEXT')
    return `TEXT:${String(value.text)
      .trim()
      .toLowerCase()
      .replace(/[.!?]$/, '')}`;
  if (value.type === 'CHOICE') return `CHOICE:${[...(value.optionIds as string[])].sort().join(',')}`;
  if (value.type === 'MAPPING')
    return `MAPPING:${(value.pairs as Json[]).map((p) => `${p.key}=${String(p.value).toLowerCase()}`).join(',')}`;
  return JSON.stringify(value);
}

export interface StubState {
  heartbeats: { activeSecondsDelta: number; state: string }[];
  saved: { itemId: string; answer: unknown; ifMatch: string | undefined }[];
  submissions: Json[];
}

interface StubOptions {
  /** Leave the audio of the fixture as it is (no URL for TTS audio before speech-service). */
  keepAudio?: boolean;
}

export async function stubPractice(
  page: Page,
  taskType: string,
  options: StubOptions = {},
): Promise<StubState & { attemptId: string }> {
  const set = fixture(taskType);
  const attemptId = `00000000-0000-4000-8000-${String(Math.abs(hash(taskType)))
    .padStart(12, '0')
    .slice(0, 12)}`;
  const source = options.keepAudio ? set.payload : (withAudio(set.payload) as Fixture['payload']);
  const itemIds = set.payload.items.map((_, i) => `10000000-0000-4000-8000-${String(i + 1).padStart(12, '0')}`);
  const answers = new Map<string, unknown>();
  let version = 1;
  let status = 'IN_PROGRESS';
  let paused = false;
  const state: StubState = { heartbeats: [], saved: [], submissions: [] };

  const visibleItem = (item: Json) => {
    const copy = redact(item) as Json;
    if (taskType === 'BUILD_A_SENTENCE') delete copy.sentence;
    if (taskType === 'COMPLETE_THE_WORDS') copy.missingLetters = String(item.suffix).length;
    return copy;
  };

  const attempt = () => ({
    attemptId,
    userId: 'u1',
    itemSetId: set.id,
    taskType,
    section: set.section,
    mode: 'PRACTICE',
    status,
    startedAt: '2026-11-10T10:00:00Z',
    estimatedSeconds: set.etsNormSeconds,
    activeSeconds: 0,
    paused,
    timingMultiplier: 1,
    stimulus: status === 'SCORED' ? source.stimulus : redact(source.stimulus),
    items: source.items.map((item, i) => ({
      itemId: itemIds[i],
      ordinal: item.ordinal,
      content: visibleItem(item),
      answer: answers.get(itemIds[i]!) ?? null,
    })),
    version,
  });

  const review = () => {
    const items = set.payload.items.map((item, i) => {
      const user = answers.get(itemIds[i]!) ?? null;
      const key = set._answers[i];
      const correct = normalize(user) === normalize(key);
      const options = (item.options as Json[] | undefined) ?? [];
      const right = new Set(((key as Json).optionIds as string[] | undefined) ?? []);
      return {
        itemId: itemIds[i],
        ordinal: item.ordinal,
        correct,
        userAnswer: user,
        correctAnswer: key,
        explanation: {
          text: String((item.explanation as Json | undefined)?.summary ?? 'Explanation.'),
          source: 'STATIC',
        },
        distractorExplanations: options
          .filter((option) => !right.has(String(option.key)))
          .map((option) => ({ optionId: option.key, text: option.rationale, source: 'STATIC' })),
      };
    });
    const correct = items.filter((item) => item.correct).length;
    return {
      attemptId,
      itemSetId: set.id,
      taskType,
      section: set.section,
      mode: 'PRACTICE',
      status: 'SCORED',
      score: { correct, total: items.length, unanswered: items.filter((i) => !i.userAnswer).length },
      activeSeconds: state.heartbeats.reduce((sum, beat) => sum + beat.activeSecondsDelta, 0),
      estimatedSeconds: set.etsNormSeconds,
      submittedAt: '2026-11-10T10:05:00Z',
      scoredAt: '2026-11-10T10:05:00Z',
      explanationLanguage: 'en',
      explanationPending: false,
      audioReplays: 0,
      abilityChange: set.section
        ? { section: set.section, thetaBefore: 0, thetaAfter: 0.12, standardError: 0.8 }
        : null,
      items,
      version,
    };
  };

  await page.route(`${AUDIO_HOST}/**`, (route) =>
    route.fulfill({ status: 200, contentType: 'audio/wav', body: silentWav() }),
  );

  await page.route(`${API}/**`, async (route: Route) => {
    const request = route.request();
    const url = new URL(request.url());
    const method = request.method();
    const json = (body: unknown, status = 200, headers: Record<string, string> = {}) =>
      route.fulfill({
        status,
        contentType: 'application/json',
        body: body === undefined ? '' : JSON.stringify(body),
        headers: {
          'access-control-allow-origin': request.headers()['origin'] ?? '*',
          'access-control-allow-credentials': 'true',
          'access-control-allow-headers': '*',
          'access-control-allow-methods': '*',
          'access-control-expose-headers': 'ETag',
          ...headers,
        },
      });
    const path = url.pathname;
    if (method === 'OPTIONS') return json({});
    if (path === '/auth/session') {
      return json({ authenticated: true, user: { id: 'u1', roles: ['STUDENT'], emailVerified: true }, csrfToken: 't' });
    }
    if (path === '/api/v1/me') {
      return json({
        id: 'u1',
        email: 'learner@toefly.test',
        displayName: 'Lea Learner',
        roles: ['STUDENT'],
        onboardingStatus: 'COMPLETED',
        createdAt: '2026-11-01T10:00:00Z',
      });
    }
    if (path === '/api/v1/item-sets') {
      return json({
        items: [
          {
            id: set.id,
            version: 1,
            taskType,
            section: set.section ?? undefined,
            examFormat: 'TOEFL_2026',
            topic: `${set.topic} ${taskType.toLowerCase()}`,
            itemCount: set.itemCount,
            estimatedMinutes: set.estimatedMinutes,
            source: { code: 'demo', title: 'Toefly samples', attribution: 'Written by the Toefly team' },
          },
        ],
        page: { hasMore: false },
      });
    }
    if (path === '/api/v1/attempts' && method === 'POST') {
      status = 'IN_PROGRESS';
      return json(attempt(), 201, { ETag: `"${version}"` });
    }
    if (path === '/api/v1/attempts' && method === 'GET') {
      return json({ items: [], page: { hasMore: false } });
    }
    if (path === `/api/v1/attempts/${attemptId}` && method === 'GET') {
      return json(attempt(), 200, { ETag: `"${version}"` });
    }
    const answer = path.match(/\/answers\/([^/]+)$/);
    if (answer && method === 'PUT') {
      const ifMatch = request.headers()['if-match'];
      if (ifMatch !== `"${version}"`) return json({ title: 'stale' }, 412);
      const body = request.postDataJSON() as { answer: unknown };
      answers.set(answer[1]!, body.answer);
      state.saved.push({ itemId: answer[1]!, answer: body.answer, ifMatch });
      version += 1;
      return json(
        {
          itemId: answer[1],
          answered: true,
          answer: body.answer,
          attemptVersion: version,
          recordedAt: new Date().toISOString(),
        },
        200,
        { ETag: `"${version}"` },
      );
    }
    if (answer && method === 'DELETE') {
      answers.delete(answer[1]!);
      version += 1;
      return route.fulfill({
        status: 204,
        headers: {
          ETag: `"${version}"`,
          'access-control-allow-origin': request.headers()['origin'] ?? '*',
          'access-control-allow-credentials': 'true',
          'access-control-expose-headers': 'ETag',
        },
      });
    }
    if (path.endsWith('/heartbeats')) {
      const beat = request.postDataJSON() as { activeSecondsDelta: number; state: string };
      state.heartbeats.push(beat);
      if (beat.state === 'PAUSED') paused = true;
      if (beat.state === 'RESUMED') paused = false;
      return route.fulfill({
        status: 204,
        headers: {
          'access-control-allow-origin': request.headers()['origin'] ?? '*',
          'access-control-allow-credentials': 'true',
        },
      });
    }
    if (path.endsWith('/submission')) {
      const body = (request.postDataJSON() ?? {}) as { finalAnswers?: { itemId: string; answer: unknown }[] };
      state.submissions.push(body as Json);
      body.finalAnswers?.forEach(({ itemId, answer: value }) => answers.set(itemId, value));
      status = 'SCORED';
      version += 1;
      return json(review(), 200, { ETag: `"${version}"` });
    }
    if (path.endsWith('/review')) return json(review());
    if (path === '/api/v1/abilities') return json({ items: [] });
    if (path === '/api/v1/item-reports') return json({ id: 'r1', status: 'OPEN' }, 201);
    return json({ title: `not stubbed: ${method} ${path}` }, 404);
  });

  return Object.assign(state, { attemptId });
}

function hash(text: string): number {
  let value = 0;
  for (const char of text) value = (value * 31 + char.charCodeAt(0)) | 0;
  return value;
}
