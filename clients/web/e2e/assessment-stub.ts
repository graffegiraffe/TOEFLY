import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import type { Page, Route } from '@playwright/test';
import { API } from './practice-stub';

/**
 * An assessment backend for the Writing screens (docs/05-api-contracts.md §6.5): one submission with versions, a
 * deterministic "AI check" that comments on known mistakes of the text, the SSE stream of the check, the band
 * estimate, and the content item sets of both Writing types. The discussion item set is the synthetic fixture of
 * assessment-service; the email one is written here. Only `pnpm dev` is needed.
 */
type Json = Record<string, unknown>;

export const SUBMISSION_ID = '50000000-0000-4000-8000-000000000001';
export const WEEK_ID = '20000000-0000-4000-8000-000000000001';
export const WRITING_TASK = '30000000-0000-4000-8000-000000000071';

const DISCUSSION = JSON.parse(
  readFileSync(
    fileURLToPath(
      new URL(
        '../../../services/assessment-service/src/test/resources/ai-fixtures/discussion-item-set.json',
        import.meta.url,
      ),
    ),
    'utf8',
  ),
) as Json & { id: string };

const EMAIL: Json & { id: string } = {
  id: '6f1e2d3c-4b5a-4c6d-8e7f-90a1b2c3d4e6',
  version: 1,
  taskType: 'WRITE_EMAIL',
  section: 'WRITING',
  examFormat: 'TOEFL_2026',
  topic: 'missed study group',
  itemCount: 1,
  estimatedMinutes: 7,
  status: 'APPROVED',
  source: { code: 'TOEFLY', title: 'TOEFLY synthetic fixtures', attribution: 'Written by the Toefly team' },
  payload: {
    stimulus: {
      scenario:
        'Yesterday you missed the weekly meeting of your study group because your train was cancelled. Write an email to the group leader.',
      recipient: { name: 'Maya Chen', relationship: 'group leader' },
      subject: 'Missed meeting yesterday',
      bullets: [
        { id: 'b1', text: 'Explain why you missed the meeting.' },
        { id: 'b2', text: 'Ask what was decided.' },
        { id: 'b3', text: 'Offer to help with the next task.' },
      ],
      timeLimitSeconds: 420,
    },
    items: [
      {
        ordinal: 1,
        rubricId: 'ETS_EMAIL_V1',
        maxScore: 5,
        keyPoints: ['reason', 'question', 'offer'],
        modelAnswers: [{ source: 'TUTOR', text: 'Dear Maya, I am sorry I missed our meeting yesterday.' }],
      },
    ],
  },
};

/** Draft text with the mistakes the stub comments on: "lives", "beter", "big". */
export const DRAFT_TEXT =
  'I agree with Felix. People lives longer when the buses are reliable, and free tickets are not enough. ' +
  'In my city the buses are often late, so many people drive. It is a big problem and beter service would help.';
/** The same text with the grammar and spelling mistakes fixed. */
export const REVISED_TEXT =
  'I agree with Felix. People live longer when the buses are reliable, and free tickets are not enough. ' +
  'In my city the buses are often late, so many people drive. It is a big problem and better service would help.';

const KNOWN_MISTAKES = [
  {
    quote: 'lives',
    category: 'GRAMMAR',
    subcategory: 'GRAMMAR.SUBJECT_VERB_AGREEMENT',
    message: 'The subject "People" is plural, so the verb has no -s.',
    suggestion: 'People live longer',
    grammarTopic: 'ROUNDUP3.U2',
    unitTitle: 'Present simple',
  },
  {
    quote: 'big',
    category: 'VOCABULARY',
    subcategory: 'VOCABULARY.IMPRECISE',
    message: 'A more precise adjective fits an academic post.',
    suggestion: 'a serious problem',
  },
  {
    quote: 'beter',
    category: 'SPELLING',
    subcategory: 'SPELLING.MISSPELLING',
    message: 'Spelling.',
    suggestion: 'better',
  },
];

export type Outcome = 'evaluated' | 'deferred' | 'evaluating';

export interface AssessmentStubOptions {
  roles?: string[];
  taskType?: 'WRITE_EMAIL' | 'WRITE_ACADEMIC_DISCUSSION';
  /** A submission that already exists: its first version in this state. */
  seed?: 'none' | Outcome;
  /** What happens after a submission: the check completes, is deferred, or keeps running. */
  afterSubmit?: Outcome;
  /** The SSE stream answers 503: the screen must fall back to polling. */
  streamFails?: boolean;
  /** The band estimate on Home. */
  band?: 'ready' | 'insufficient' | 'missing';
  /** Serve a week with this Writing task (the week entry point). */
  weekTask?: boolean;
}

export interface AssessmentStubState {
  creates: Json[];
  saves: { ifMatch: string | undefined; text: string; elapsedSeconds: number }[];
  submits: number;
  newVersions: number;
  streamRequests: number;
  polls: number;
}

interface Version {
  versionNo: number;
  status: string;
  text: string;
  activeSeconds: number;
  submittedAt: string | null;
  evaluation: Json | null;
  score: number | null;
}

const uuid = (n: number) => `60000000-0000-4000-8000-${String(n).padStart(12, '0')}`;

export async function stubAssessment(page: Page, options: AssessmentStubOptions = {}): Promise<AssessmentStubState> {
  const roles = options.roles ?? ['STUDENT'];
  const taskType = options.taskType ?? 'WRITE_ACADEMIC_DISCUSSION';
  const set = taskType === 'WRITE_EMAIL' ? EMAIL : DISCUSSION;
  const afterSubmit = options.afterSubmit ?? 'evaluated';
  const state: AssessmentStubState = {
    creates: [],
    saves: [],
    submits: 0,
    newVersions: 0,
    streamRequests: 0,
    polls: 0,
  };
  let exists = false;
  let weekTaskId: string | null = null;
  let aggregate = 1;
  let annotationNo = 0;
  const versions: Version[] = [];

  const evaluate = (version: Version) => {
    const previous = versions.find((item) => item.versionNo === version.versionNo - 1);
    const annotations: Json[] = KNOWN_MISTAKES.filter((mistake) => version.text.includes(mistake.quote)).map(
      (mistake) => {
        const start = version.text.indexOf(mistake.quote);
        return {
          id: uuid(++annotationNo),
          start,
          end: start + mistake.quote.length,
          anchorQuote: mistake.quote,
          category: mistake.category,
          subcategory: mistake.subcategory,
          severity: 'MAJOR',
          message: mistake.message,
          suggestion: mistake.suggestion,
          grammarTopic: mistake.grammarTopic ?? null,
          unitTitle: mistake.unitTitle ?? null,
          status: 'OPEN',
        };
      },
    );
    annotations.push({
      id: uuid(++annotationNo),
      start: null,
      end: null,
      anchorQuote: '',
      category: 'COHERENCE',
      subcategory: 'COHERENCE.DEVELOPMENT',
      severity: 'MINOR',
      message: 'Add an example from your own experience to support your point.',
      suggestion: null,
      grammarTopic: null,
      unitTitle: null,
      status: 'OPEN',
    });
    if (previous?.evaluation) {
      for (const annotation of previous.evaluation.annotations as Json[]) {
        if (annotation.start === null) continue;
        annotation.status = version.text.includes(String(annotation.anchorQuote)) ? 'STILL_PRESENT' : 'FIXED';
      }
    }
    const fixedAll = !version.text.includes('lives');
    const score = fixedAll ? 4 : 3;
    version.status = 'EVALUATED';
    version.score = score;
    version.evaluation = {
      id: uuid(900 + version.versionNo),
      versionNo: version.versionNo,
      evaluator: 'AI',
      tutorId: null,
      rubricId: taskType === 'WRITE_EMAIL' ? 'ETS_EMAIL_V1' : 'ETS_DISCUSSION_V1',
      finalScore: score,
      overallScore: score,
      criteria: [
        { criterion: 'conventions', level: score, note: 'Responds to the other students.' },
        { criterion: 'taskAchievement', level: score, note: 'Answers the professor’s question.' },
        { criterion: 'development', level: 3, note: 'One reason, briefly supported.' },
        { criterion: 'organization', level: 3, note: '' },
        { criterion: 'languageRange', level: 3, note: '' },
        { criterion: 'accuracy', level: fixedAll ? 4 : 2, note: '' },
      ],
      strengths: ['You take a clear position.'],
      priorities: ['Check subject–verb agreement.', 'Support your reason with an example.'],
      annotations,
      agreement: 'AGREED',
      needsHumanReview: false,
      capsApplied: [],
      integrityFlags: [],
      degraded: false,
      createdAt: '2026-11-18T10:01:00Z',
    };
    aggregate += 1;
  };

  const settle = (version: Version) => {
    if (afterSubmit === 'evaluated') evaluate(version);
    else if (afterSubmit === 'deferred') {
      version.status = 'EVALUATION_DEFERRED';
      aggregate += 1;
    }
  };

  if (options.seed && options.seed !== 'none') {
    exists = true;
    versions.push({
      versionNo: 1,
      status: 'EVALUATING',
      text: DRAFT_TEXT,
      activeSeconds: 540,
      submittedAt: '2026-11-18T10:00:00Z',
      evaluation: null,
      score: null,
    });
    if (options.seed === 'evaluated') evaluate(versions[0]!);
    if (options.seed === 'deferred') versions[0]!.status = 'EVALUATION_DEFERRED';
  }

  const words = (text: string) => text.trim().split(/\s+/).filter(Boolean).length;
  const wireVersion = (version: Version): Json => {
    const previous = versions.find((item) => item.versionNo === version.versionNo - 1);
    return {
      versionNo: version.versionNo,
      status: version.status,
      text: version.text,
      wordCount: words(version.text),
      activeSeconds: version.activeSeconds,
      overtimeSeconds: 0,
      submittedAt: version.submittedAt,
      countsForEstimate: version.versionNo === 1,
      evaluation: version.evaluation,
      tutorEvaluation: null,
      effectiveScore: version.score,
      scoreDelta:
        previous?.score !== null && previous?.score !== undefined && version.score !== null
          ? version.score - previous.score
          : null,
    };
  };
  const current = () => versions[versions.length - 1]!;
  const submission = (): Json => ({
    id: SUBMISSION_ID,
    userId: 'u1',
    itemSetId: set.id,
    itemSetVersion: 1,
    taskType,
    mode: 'PRACTICE',
    weekTaskId,
    examSessionId: null,
    status: current().status,
    currentVersionNo: current().versionNo,
    version: aggregate,
    createdAt: '2026-11-18T09:50:00Z',
    timeLimitSeconds: taskType === 'WRITE_EMAIL' ? 420 : 600,
    versions: versions.map(wireVersion),
    deferred:
      current().status === 'EVALUATION_DEFERRED'
        ? { attempts: 1, nextAttemptAt: '2026-11-18T10:05:00Z', retryAfterSeconds: 60 }
        : null,
  });

  const band = (): Json | null => {
    if (options.band === 'missing') return null;
    const ready = options.band === 'ready';
    const section = (name: string, low: number, high: number, n: number, remaining: number) =>
      ready || name === 'READING'
        ? {
            section: name,
            status: 'READY',
            pointBand: low,
            low,
            high,
            cefrLow: 'B2',
            cefrHigh: 'B2',
            comparableLow: 72,
            comparableHigh: 83,
            confidence: 'MEDIUM',
            n,
            remaining: 0,
          }
        : { section: name, status: 'NEED_MORE_DATA', n, remaining };
    return {
      sections: [
        section('READING', 4, 4.5, 40, 0),
        section('LISTENING', 4, 4.5, 12, 8),
        section('WRITING', 4, 4.5, 0, 2),
        section('SPEAKING', 3.5, 4, 0, 2),
      ],
      overall: ready
        ? {
            pointBand: 4,
            low: 4,
            high: 4.5,
            cefr: 'B2',
            cefrLow: 'B2',
            cefrHigh: 'B2',
            comparableLow: 72,
            comparableHigh: 83,
          }
        : null,
      needsMoreData: ready ? [] : ['LISTENING', 'WRITING', 'SPEAKING'],
      asOf: '2026-11-18T10:00:00Z',
    };
  };

  const week = (): Json => {
    const day = new Date();
    const iso = (offset: number) => {
      const date = new Date(day.getFullYear(), day.getMonth(), day.getDate() + offset);
      const pad = (n: number) => String(n).padStart(2, '0');
      return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
    };
    return {
      weekId: WEEK_ID,
      planId: '20000000-0000-4000-8000-0000000000aa',
      userId: 'u1',
      weekNo: 1,
      status: 'IN_PROGRESS',
      startDate: iso(0),
      endDate: iso(6),
      dayCount: 7,
      currentDayIndex: 1,
      progressPercent: 0,
      mockExam: false,
      rationale: null,
      autoPublished: false,
      pendingSlots: 0,
      rebalance: { lastRebalancedAt: null, nextAllowedAt: null, excludedTaskIds: [] },
      sections: [
        {
          section: 'WRITING',
          tasks: [
            {
              taskId: WRITING_TASK,
              weekId: WEEK_ID,
              kind: 'ITEM_SET',
              section: 'WRITING',
              taskType,
              dayIndex: 1,
              ordinal: 1,
              status: exists && current().status !== 'DRAFT' ? 'DONE' : 'PENDING',
              skippedReason: null,
              estimatedMinutes: 10,
              title: 'Email: Missed study group',
              itemSetId: set.id,
              provenance: 'GENERATED',
              legacy: false,
              lastAttemptId: null,
              completedAt: null,
            },
          ],
        },
      ],
      days: Array.from({ length: 7 }, (_, i) => ({
        dayIndex: i + 1,
        date: iso(i),
        estimatedMinutes: i === 0 ? 10 : 0,
        taskIds: i === 0 ? [WRITING_TASK] : [],
      })),
      version: 1,
    };
  };

  await page.route(`${API}/**`, async (route: Route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    const method = request.method();
    const cors = {
      'access-control-allow-origin': request.headers()['origin'] ?? '*',
      'access-control-allow-credentials': 'true',
      'access-control-allow-headers': '*',
      'access-control-allow-methods': '*',
      'access-control-expose-headers': 'ETag',
    };
    const json = (body: unknown, status = 200, headers: Record<string, string> = {}) =>
      route.fulfill({
        status,
        contentType: status >= 400 ? 'application/problem+json' : 'application/json',
        body: JSON.stringify(body),
        headers: { ...cors, ...headers },
      });
    const etag = { ETag: `"${aggregate}"` };
    if (method === 'OPTIONS') return json({});
    if (path === '/auth/session') {
      return json({ authenticated: true, user: { id: 'u1', roles, emailVerified: true }, csrfToken: 't' });
    }
    if (path === '/api/v1/me') {
      return json({
        id: 'u1',
        email: 'learner@toefly.test',
        displayName: 'Lea Learner',
        roles,
        onboardingStatus: 'COMPLETED',
        createdAt: '2026-11-01T10:00:00Z',
      });
    }
    if (path === '/api/v1/me/study-goal') {
      return json({ overallTarget: 4.5, examBooked: false, examDate: null, minutesPerDay: 60, revision: 1 });
    }
    if (path === '/api/v1/band-estimates/current') {
      const body = band();
      return body ? json(body) : json({ code: 'NOT_FOUND', status: 404, title: 'Not found', type: 'about:blank' }, 404);
    }
    if (path === '/api/v1/item-sets') {
      const requested = url.searchParams.get('taskType');
      const items = [EMAIL, DISCUSSION]
        .filter((item) => !requested || item.taskType === requested)
        .map((item) => {
          const summary: Json = { ...item };
          delete summary.payload;
          return summary;
        });
      return json({ items, page: { hasMore: false, limit: 50 } });
    }
    if (path === `/api/v1/item-sets/${EMAIL.id}`) return json(EMAIL);
    if (path === `/api/v1/item-sets/${DISCUSSION.id}`) return json(DISCUSSION);
    if (path === '/api/v1/attempts' && method === 'GET')
      return json({ items: [], page: { hasMore: false, limit: 20 } });
    if (path === `/api/v1/weeks/${WEEK_ID}` && options.weekTask) return json(week());

    if (path === '/api/v1/submissions' && method === 'POST') {
      const body = request.postDataJSON() as Json;
      state.creates.push({ ...body, idempotencyKey: request.headers()['idempotency-key'] });
      exists = true;
      weekTaskId = (body.weekTaskId as string | null) ?? null;
      versions.splice(0, versions.length, {
        versionNo: 1,
        status: 'DRAFT',
        text: '',
        activeSeconds: 0,
        submittedAt: null,
        evaluation: null,
        score: null,
      });
      aggregate = 1;
      return json(submission(), 201, etag);
    }
    if (path === '/api/v1/submissions' && method === 'GET') {
      const status = url.searchParams.get('status');
      const filterTask = url.searchParams.get('weekTaskId');
      const items =
        exists && (!status || status === current().status) && (!filterTask || filterTask === weekTaskId)
          ? [
              {
                id: SUBMISSION_ID,
                itemSetId: set.id,
                taskType,
                mode: 'PRACTICE',
                weekTaskId,
                status: current().status,
                currentVersionNo: current().versionNo,
                latestScore: current().score,
                createdAt: '2026-11-18T09:50:00Z',
              },
            ]
          : [];
      return json({ items, page: { hasMore: false, limit: 20 } });
    }
    if (!path.startsWith(`/api/v1/submissions/${SUBMISSION_ID}`) || !exists) {
      if (path.startsWith('/api/v1/submissions/')) return json({ code: 'NOT_FOUND', status: 404 }, 404);
      return json({ title: `not stubbed: ${method} ${path}` }, 404);
    }
    const rest = path.slice(`/api/v1/submissions/${SUBMISSION_ID}`.length);
    if (rest === '' && method === 'GET') {
      if (current().status === 'EVALUATING') state.polls += 1;
      return json(submission(), 200, etag);
    }
    if (rest === '/versions/current' && method === 'PUT') {
      const ifMatch = request.headers()['if-match'];
      const body = request.postDataJSON() as { text: string; elapsedSeconds: number };
      state.saves.push({ ifMatch, text: body.text, elapsedSeconds: body.elapsedSeconds });
      if (ifMatch !== `"${aggregate}"`) return json({ code: 'VERSION_MISMATCH', status: 412 }, 412, etag);
      if (current().status !== 'DRAFT') return json({ code: 'CONFLICT', status: 409 }, 409);
      current().text = body.text;
      current().activeSeconds = body.elapsedSeconds;
      aggregate += 1;
      return json(wireVersion(current()), 200, { ETag: `"${aggregate}"` });
    }
    if (rest === '/submission' && method === 'POST') {
      state.submits += 1;
      const version = current();
      if (version.status === 'DRAFT') {
        version.status = 'EVALUATING';
        version.submittedAt = new Date().toISOString();
        aggregate += 1;
      }
      return json(submission(), 202, { ETag: `"${aggregate}"` });
    }
    if (rest === '/versions' && method === 'POST') {
      state.newVersions += 1;
      const previous = current();
      versions.push({
        versionNo: previous.versionNo + 1,
        status: 'DRAFT',
        text: previous.text,
        activeSeconds: 0,
        submittedAt: null,
        evaluation: null,
        score: null,
      });
      aggregate += 1;
      return json(wireVersion(current()), 201, { ETag: `"${aggregate}"` });
    }
    if (rest === '/events' && method === 'GET') {
      state.streamRequests += 1;
      if (options.streamFails) {
        return route.fulfill({ status: 503, headers: cors, body: '' });
      }
      const version = current();
      let body = 'retry: 3000\n\n';
      body += `id: ${SUBMISSION_ID}:1\nevent: evaluation.status\ndata: ${JSON.stringify({ submissionId: SUBMISSION_ID, versionNo: version.versionNo, status: version.status })}\n\n`;
      if (version.status === 'EVALUATING') {
        settle(version);
        const settled: string = version.status;
        if (settled === 'EVALUATED') {
          body += `id: ${SUBMISSION_ID}:2\nevent: evaluation.completed\ndata: ${JSON.stringify({ submissionId: SUBMISSION_ID, versionNo: version.versionNo, evaluationId: uuid(900 + version.versionNo), score: version.score })}\n\n`;
        } else if (settled === 'EVALUATION_DEFERRED') {
          body += `id: ${SUBMISSION_ID}:2\nevent: evaluation.deferred\ndata: ${JSON.stringify({ submissionId: SUBMISSION_ID, retryAfterSeconds: 60 })}\n\n`;
        }
      }
      return route.fulfill({
        status: 200,
        contentType: 'text/event-stream',
        headers: { ...cors, 'cache-control': 'no-cache' },
        body,
      });
    }
    return json({ title: `not stubbed: ${method} ${path}` }, 404);
  });

  return state;
}

/** Pastes text from outside the editor, the way a browser delivers a clipboard paste. */
export async function pasteFromOutside(page: Page, selector: string, text: string) {
  await page.locator(selector).evaluate((element, value) => {
    const data = new DataTransfer();
    data.setData('text/plain', value);
    element.dispatchEvent(new ClipboardEvent('paste', { clipboardData: data, bubbles: true, cancelable: true }));
  }, text);
}
