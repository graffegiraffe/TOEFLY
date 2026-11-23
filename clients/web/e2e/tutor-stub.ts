import type { Page, Route } from '@playwright/test';
import { API } from './practice-stub';

/**
 * A tutor-agent backend for the chat panel and the weekly review (services/tutor-agent-service/src/main/openapi,
 * docs/06-ai-agent.md §4.9). The first answer of a thread is cut after two tokens, as a dropped connection would be,
 * and the rest comes from the resume endpoint with Last-Event-ID. The second message is refused with 429.
 * Register it after the planning or practice stub: other requests fall through to them.
 */
type Json = Record<string, unknown>;

export const THREAD_ID = '60000000-0000-4000-8000-000000000001';
export const MESSAGE_ID = '60000000-0000-4000-8000-000000000002';
export const ACTION_ID = '60000000-0000-4000-8000-000000000003';
export const REVIEW_ID = '70000000-0000-4000-8000-000000000001';

export interface TutorStubState {
  threads: Json[];
  sent: { threadId: string; body: Json }[];
  resumes: (string | undefined)[];
  confirmations: string[];
  languages: string[];
}

interface Options {
  /** Rights of the learner; without AI_TUTOR_CHAT the button opens the Paywall. */
  entitlements?: string[];
  /** The review of Week 1: `null` while it is being prepared. */
  review?: Json | null;
}

const event = (id: number, name: string, data: unknown) =>
  `id: ${id}\nevent: ${name}\ndata: ${JSON.stringify(data)}\n\n`;

export function weeklyReview(weekId: string, overrides: Json = {}): Json {
  return {
    reviewId: REVIEW_ID,
    weekId,
    weekNo: 1,
    generatedAt: '2026-11-22T10:00:00Z',
    language: 'EN',
    aiFallback: false,
    lowActivity: false,
    headline: 'Reading is up, articles still slip',
    summary: 'You did 5 of 6 tasks in 2 h 5 min. Reading accuracy rose to 83%.',
    stats: {
      tasksTotal: 6,
      tasksDone: 5,
      progressPercent: 83,
      activeMinutes: 125,
      sections: [
        { section: 'READING', tasksTotal: 2, tasksDone: 2, minutesPlanned: 30, minutesDone: 34 },
        { section: 'LISTENING', tasksTotal: 1, tasksDone: 1, minutesPlanned: 10, minutesDone: 9 },
        { section: 'GRAMMAR', tasksTotal: 2, tasksDone: 1, minutesPlanned: 40, minutesDone: 20 },
      ],
    },
    scores: [
      { taskType: 'READ_DAILY_LIFE', attempts: 2, accuracyPercent: 83.3 },
      { taskType: 'WRITE_ACADEMIC_DISCUSSION', attempts: 1, averageScore: 3.5 },
    ],
    bandSnapshot: { overall: 4, reading: 4.5, listening: 4, writing: 3.5, speaking: null, overallDelta: 0.5 },
    topMistakes: [
      {
        subcategory: 'GRAMMAR.ARTICLES',
        category: 'GRAMMAR',
        count: 4,
        pattern: 'Missing "the" before a known noun',
        example: { wrong: 'I went to library', right: 'I went to the library' },
        advice: 'Ask whether the reader knows which one you mean.',
        unitRefs: [{ code: 'ROUNDUP5.U3', title: 'Articles' }],
      },
      {
        subcategory: 'GRAMMAR.SUBJECT_VERB_AGREEMENT',
        category: 'GRAMMAR',
        count: 2,
        pattern: 'Verb without -s after he, she or it',
        example: { wrong: 'she walk', right: 'she walks' },
        advice: 'Find the subject first, then check the verb.',
        unitRefs: [{ code: 'ROUNDUP5.U1', title: 'Present simple' }],
      },
    ],
    strengths: ['Reading: you find the main idea fast.', 'You studied five days in a row.'],
    nextWeekFocus: [{ subcategory: 'GRAMMAR.ARTICLES', why: 'Four slips this week.' }],
    motivation: 'One more steady week and articles will stop costing you points.',
    tutorNote: null,
    ...overrides,
  };
}

export async function stubTutor(page: Page, options: Options = {}): Promise<TutorStubState> {
  const state: TutorStubState = { threads: [], sent: [], resumes: [], confirmations: [], languages: [] };
  const messages: Json[] = [];
  const rights = options.entitlements ?? ['AI_TUTOR_CHAT', 'WEEKLY_REVIEW', 'AI_WRITING_CHECK'];

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
    };
    const json = (body: unknown, status = 200) =>
      route.fulfill({ status, contentType: 'application/json', headers: cors, body: JSON.stringify(body) });
    const sse = (body: string) =>
      route.fulfill({
        status: 200,
        contentType: 'text/event-stream',
        headers: { ...cors, 'cache-control': 'no-cache' },
        body,
      });

    if (method === 'OPTIONS') return route.fallback();
    if (path === '/api/v1/entitlements') {
      return json({
        userId: 'u1',
        entitlementsVersion: 1,
        planTierCode: rights.includes('AI_TUTOR_CHAT') ? 'STUDENT_PRO' : 'STUDENT_FREE',
        entitlements: rights,
        grants: [],
        freeAiChecks: { limit: 10, used: 0, remaining: 10 },
        changedAt: new Date().toISOString(),
      });
    }
    if (path === '/api/v1/chat-threads' && method === 'POST') {
      const body = request.postDataJSON() as Json;
      const thread = {
        threadId: THREAD_ID,
        title: 'Question 1',
        context: body.context ?? null,
        language: 'EN',
        createdAt: new Date().toISOString(),
        lastMessageAt: null,
        messageCount: 0,
      };
      state.threads.push(thread);
      return json(thread, 201);
    }
    if (path === '/api/v1/chat-threads' && method === 'GET') {
      return json({ items: state.threads, page: { hasMore: false, limit: 50, nextCursor: null } });
    }
    if (path === `/api/v1/chat-threads/${THREAD_ID}` && method === 'PATCH') {
      const body = request.postDataJSON() as { language: string };
      state.languages.push(body.language);
      Object.assign(state.threads[0] ?? {}, { language: body.language });
      return json(state.threads[0]);
    }
    if (path === `/api/v1/chat-threads/${THREAD_ID}/messages` && method === 'GET') {
      return json({ items: messages, page: { hasMore: false, limit: 100, nextCursor: null } });
    }
    if (path === `/api/v1/chat-threads/${THREAD_ID}/messages` && method === 'POST') {
      const body = request.postDataJSON() as Json;
      state.sent.push({ threadId: THREAD_ID, body });
      if (state.sent.length > 1) {
        return json(
          {
            type: 'https://toefly.app/problems/rate-limited',
            title: 'Too many messages',
            status: 429,
            code: 'RATE_LIMITED',
          },
          429,
        );
      }
      messages.push({
        messageId: '60000000-0000-4000-8000-000000000011',
        role: 'USER',
        text: body.text,
        status: 'COMPLETE',
        createdAt: new Date().toISOString(),
      });
      // The connection drops after the second token: no `done`.
      return sse(
        ': ping\n\n' +
          event(1, 'meta', { messageId: MESSAGE_ID, intent: 'EXPLAIN_MISTAKE', language: 'EN' }) +
          event(2, 'tool', { name: 'getAttemptReview', status: 'started', label: 'Looking at your answers' }) +
          event(3, 'tool', { name: 'getAttemptReview', status: 'finished', label: 'Looked at your answers' }) +
          event(4, 'token', { t: "I won't give the answer yet. " }) +
          event(5, 'token', { t: 'What does the notice say about **weekends**?' }),
      );
    }
    if (path === `/api/v1/chat-threads/${THREAD_ID}/messages/${MESSAGE_ID}/stream` && method === 'GET') {
      state.resumes.push(request.headers()['last-event-id']);
      const text =
        "I won't give the answer yet. What does the notice say about **weekends**? Then compare it with option B.";
      messages.push({
        messageId: MESSAGE_ID,
        role: 'ASSISTANT',
        text,
        status: 'COMPLETE',
        intent: 'EXPLAIN_MISTAKE',
        actions: [
          { actionId: ACTION_ID, type: 'ASSIGN_EXTRA_TASK', title: 'Notices practice, 6 min', status: 'PENDING' },
        ],
        tools: ['getAttemptReview'],
        createdAt: new Date().toISOString(),
      });
      return sse(
        event(6, 'token', { t: ' Then compare it with option B.' }) +
          event(7, 'action', {
            actionId: ACTION_ID,
            type: 'ASSIGN_EXTRA_TASK',
            title: 'Notices practice, 6 min',
            confirmUrl: `/api/v1/pending-actions/${ACTION_ID}:confirm`,
          }) +
          event(8, 'done', { messageId: MESSAGE_ID, usage: { inputTokens: 900, outputTokens: 40 } }),
      );
    }
    if (path === `/api/v1/pending-actions/${ACTION_ID}:confirm` && method === 'POST') {
      state.confirmations.push(ACTION_ID);
      const message = messages.find((item) => item.messageId === MESSAGE_ID);
      if (message) message.actions = [{ ...(message.actions as Json[])[0], status: 'CONFIRMED' }];
      return json({ actionId: ACTION_ID, type: 'ASSIGN_EXTRA_TASK', status: 'CONFIRMED', weekTaskId: null });
    }
    if (path === '/api/v1/weekly-reviews' && method === 'GET') {
      const weekId = url.searchParams.get('weekId');
      if (options.review === undefined || options.review === null) {
        return json({ items: [], page: { hasMore: false, limit: 1, nextCursor: null } });
      }
      const items = !weekId || options.review.weekId === weekId ? [options.review] : [];
      return json({ items, page: { hasMore: false, limit: 1, nextCursor: null } });
    }
    return route.fallback();
  });

  return state;
}
