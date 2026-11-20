import type { Page } from '@playwright/test';
import { API } from './practice-stub';

/**
 * An analytics backend for Home, Progress and the admin metrics (docs/05-api-contracts.md §6.14): read models of a
 * learner in week 2 with a 3-day streak, a slow Reading pace and a band history with a gap. Other services answer
 * "nothing yet", so only `pnpm dev` is needed; analytics-service itself is covered by its component tests.
 */
type Json = Record<string, unknown>;

function isoDay(offset: number): string {
  const day = new Date();
  day.setUTCHours(0, 0, 0, 0);
  day.setUTCDate(day.getUTCDate() + offset);
  return day.toISOString().slice(0, 10);
}

const asOf = () => new Date(Date.now() - 2 * 60_000).toISOString();

function streak(): Json {
  return {
    current: 3,
    longest: 6,
    todayDone: true,
    goalMinutes: 30,
    today: isoDay(0),
    timezone: 'Europe/Minsk',
    todayActiveSeconds: 2100,
    todayGoalProgress: 1,
    days: Array.from({ length: 30 }, (_, i) => ({ date: isoDay(i - 29), done: i >= 27 || (i >= 10 && i <= 15) })),
    asOf: asOf(),
  };
}

function dashboard(): Json {
  return {
    userId: 'u1',
    asOf: asOf(),
    today: isoDay(0),
    timezone: 'Europe/Minsk',
    currentWeek: {
      weekId: 'w2',
      weekNo: 2,
      day1: isoDay(-3),
      day: 4,
      progressPercent: 44,
      tasksTotal: 18,
      completed: false,
    },
    bandEstimate: null,
    theta: [],
    lastResults: [],
    examDate: isoDay(38),
    examCountdownDays: 38,
    targets: { overall: 5 },
    todayGoal: { date: isoDay(0), activeSeconds: 2100, goalMinutes: 30, goalProgress: 1, goalMet: true, tasksDone: 2 },
    streak: { current: 3, longest: 6, todayDone: true },
    weekActiveSeconds: 15_000,
    weeks: [
      {
        weekNo: 1,
        day1: isoDay(-10),
        progressPercent: 78,
        tasksDone: 14,
        tasksTotal: 18,
        completed: true,
        overall: { point: 4, low: 3.5, high: 4.5 },
        sections: [
          { section: 'READING', point: 4.5 },
          { section: 'LISTENING', point: 4 },
        ],
      },
      {
        weekNo: 2,
        day1: isoDay(-3),
        progressPercent: 44,
        tasksDone: 8,
        tasksTotal: 18,
        completed: false,
        overall: { point: 4.5, low: 4, high: 5 },
        sections: [{ section: 'READING', point: 5 }],
      },
    ],
  };
}

function timeReport(): Json {
  const byDay = [-6, -5, -4, -3, -2, -1, 0].map((offset, i) => ({
    date: isoDay(offset),
    activeSeconds: [1800, 0, 2400, 900, 2700, 1200, 2100][i],
    goalMet: [true, false, true, false, true, false, true][i],
    bySection: [
      { section: 'READING', activeSeconds: [1200, 0, 1200, 600, 1500, 600, 900][i] },
      { section: 'LISTENING', activeSeconds: [600, 0, 600, 0, 600, 0, 600][i] },
      { section: 'GRAMMAR', activeSeconds: [0, 0, 600, 300, 600, 600, 600][i] },
    ],
  }));
  return {
    from: isoDay(-6),
    to: isoDay(1),
    totalSeconds: 13_100,
    plannedSeconds: 7 * 30 * 60,
    bySection: [
      { section: 'READING', activeSeconds: 6000 },
      { section: 'LISTENING', activeSeconds: 2400 },
      { section: 'GRAMMAR', activeSeconds: 3300 },
    ],
    byTaskType: [
      {
        taskType: 'READ_ACADEMIC_PASSAGE',
        activeSeconds: 2088,
        normSeconds: 1440,
        paceRatio: 1.45,
        verdict: 'SLOWER',
        attempts: 4,
        slowAttempts: 3,
        fastAttempts: 0,
      },
      {
        taskType: 'LISTEN_CHOOSE_RESPONSE',
        activeSeconds: 600,
        normSeconds: 600,
        paceRatio: 1,
        verdict: 'ON_PACE',
        attempts: 4,
        slowAttempts: 0,
        fastAttempts: 0,
      },
      {
        taskType: 'GRAMMAR_EXERCISE',
        activeSeconds: 3300,
        normSeconds: 0,
        paceRatio: null,
        verdict: 'NO_NORM',
        attempts: 0,
        slowAttempts: 0,
        fastAttempts: 0,
      },
    ],
    byDay,
  };
}

const totals = (calls: number, cost: number, fallback: number) => ({
  calls,
  tokensIn: calls * 1200,
  tokensOut: calls * 300,
  costMicroUsd: cost,
  fallbackCalls: fallback,
  cacheHits: 3,
  fallbackShare: fallback / calls,
});

export interface AnalyticsStubState {
  rebuilds: string[];
}

export async function stubAnalytics(page: Page, roles: string[] = ['STUDENT']): Promise<AnalyticsStubState> {
  const state: AnalyticsStubState = { rebuilds: [] };
  let jobPolls = 0;
  await page.route(`${API}/**`, async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    const json = (body: unknown, status = 200) =>
      route.fulfill({
        status,
        contentType: status >= 400 ? 'application/problem+json' : 'application/json',
        body: JSON.stringify(body),
        headers: {
          'access-control-allow-origin': request.headers()['origin'] ?? '*',
          'access-control-allow-credentials': 'true',
          'access-control-allow-headers': '*',
          'access-control-allow-methods': '*',
        },
      });
    if (request.method() === 'OPTIONS') return json({});
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
    if (path === '/api/v1/dashboards/student') return json(dashboard());
    if (path === '/api/v1/streaks/me') return json(streak());
    if (path === '/api/v1/time-reports') return json(timeReport());
    if (path === '/api/v1/admin/metrics/product') {
      return json({
        from: isoDay(-29),
        to: isoDay(1),
        funnel: { registered: 24, onboarded: 20, planCreated: 18, firstTaskDone: 15, activated: 12 },
        retention: [
          { name: 'D1', startDay: 1, endDay: 1, eligible: 22, retained: 15, rate: 15 / 22 },
          { name: 'D7', startDay: 7, endDay: 13, eligible: 14, retained: 7, rate: 0.5 },
          { name: 'D28', startDay: 28, endDay: 34, eligible: 0, retained: 0, rate: null },
        ],
        weekCompletionRate: 0.71,
        medianStudyDaysRatio: 0.61,
        learnersInSdr: 13,
        daily: [],
      });
    }
    if (path === '/api/v1/admin/metrics/ai-usage') {
      return json({
        from: isoDay(-29),
        to: isoDay(1),
        totals: totals(406, 81_200, 12),
        byProvider: [
          { key: 'GROQ', totals: totals(380, 80_000, 6) },
          { key: 'OLLAMA', totals: totals(26, 1200, 6) },
        ],
        byModel: [{ key: 'GROQ / openai/gpt-oss-120b', totals: totals(300, 70_000, 4) }],
        byPrompt: [{ key: 'writing.grade.discussion', totals: totals(220, 60_000, 3) }],
        byDay: [],
        activeLearners: 13,
        costPerActiveLearnerMicroUsd: 6246,
      });
    }
    const rebuild = path.match(/^\/api\/v1\/admin\/projections\/([a-z-]+)\/rebuild$/);
    if (rebuild && request.method() === 'POST') {
      state.rebuilds.push(rebuild[1]!);
      jobPolls = 0;
      return json({
        id: 'job-1',
        projection: rebuild[1],
        status: 'RUNNING',
        requestedAt: new Date().toISOString(),
        eventsReplayed: 0,
      });
    }
    if (path === '/api/v1/admin/projections/rebuilds/job-1') {
      jobPolls += 1;
      const done = jobPolls > 1;
      return json({
        id: 'job-1',
        projection: state.rebuilds.at(-1),
        status: done ? 'COMPLETED' : 'RUNNING',
        requestedAt: new Date().toISOString(),
        finishedAt: done ? new Date().toISOString() : null,
        eventsReplayed: done ? 448 : 0,
      });
    }
    // Planning, assessment and profile extras of Home: nothing yet.
    return json(
      { type: 'https://toefly.app/problems/not-found', title: 'Not found', status: 404, code: 'NOT_FOUND' },
      404,
    );
  });
  return state;
}
