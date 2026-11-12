import type { Page, Route } from '@playwright/test';
import { API, fixture, stubPractice, type StubState } from './practice-stub';

/**
 * A planning backend for the week screens (docs/05-api-contracts.md §7.2): one plan of four weeks, Week 1 in
 * progress with tasks of every kind the phase builds. Attempts go to the practice stub; a submission marks its
 * week task done, as `attempt-scored` does in planning-service, so the progress of the week grows.
 */
type Json = Record<string, unknown>;

export const WEEK_ID = '20000000-0000-4000-8000-000000000001';
export const PLAN_ID = '20000000-0000-4000-8000-0000000000aa';
export const READING_TASK = '30000000-0000-4000-8000-000000000001';
export const RESOURCE_TASK = '30000000-0000-4000-8000-000000000005';
export const GRAMMAR_TASK = '30000000-0000-4000-8000-000000000003';

function isoDay(offset: number): string {
  const date = new Date();
  date.setDate(date.getDate() + offset);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

interface Task {
  taskId: string;
  kind: string;
  section: string;
  taskType?: string;
  dayIndex: number;
  ordinal: number;
  status: string;
  skippedReason?: string | null;
  estimatedMinutes: number;
  title: string;
  itemSetId?: string;
  unitId?: string;
  resourceUrl?: string;
  lastAttemptId?: string | null;
}

export interface PlanningStubState {
  tasks: Task[];
  completions: string[];
  finished: number;
  practice: StubState & { attemptId: string };
}

export interface PlanningStubOptions {
  /** Rebalanced week: two tasks left the week (AC-04.05.2). */
  rebalanced?: boolean;
  /** Overdue task from the day before (AC-04.04.1). */
  overdue?: boolean;
}

export async function stubPlanning(page: Page, options: PlanningStubOptions = {}): Promise<PlanningStubState> {
  const reading = fixture('READ_DAILY_LIFE');
  const listening = fixture('LISTEN_CHOOSE_RESPONSE');
  const practice = await stubPractice(page, 'READ_DAILY_LIFE');
  const tasks: Task[] = [
    {
      taskId: READING_TASK,
      kind: 'ITEM_SET',
      section: 'READING',
      taskType: 'READ_DAILY_LIFE',
      dayIndex: 1,
      ordinal: 1,
      status: 'PENDING',
      estimatedMinutes: 6,
      title: 'Read in Daily Life: Library hours',
      itemSetId: reading.id,
    },
    {
      taskId: '30000000-0000-4000-8000-000000000002',
      kind: 'ITEM_SET',
      section: 'LISTENING',
      taskType: 'LISTEN_CHOOSE_RESPONSE',
      dayIndex: options.overdue ? 0 : 2,
      ordinal: 1,
      status: 'PENDING',
      estimatedMinutes: 5,
      title: 'Choose a Response: Campus life',
      itemSetId: listening.id,
    },
    {
      taskId: GRAMMAR_TASK,
      kind: 'GRAMMAR_UNIT',
      section: 'GRAMMAR',
      dayIndex: 1,
      ordinal: 2,
      status: 'PENDING',
      estimatedMinutes: 20,
      title: 'Round-Up 1: Present simple',
      unitId: '40000000-0000-4000-8000-000000000001',
    },
    {
      taskId: '30000000-0000-4000-8000-000000000004',
      kind: 'VOCABULARY_UNIT',
      section: 'VOCABULARY',
      dayIndex: 3,
      ordinal: 1,
      status: 'PENDING',
      estimatedMinutes: 15,
      title: 'Collins Vocabulary 1: Personal information',
      unitId: '40000000-0000-4000-8000-000000000002',
    },
    {
      taskId: RESOURCE_TASK,
      kind: 'EXTERNAL_RESOURCE',
      section: 'RESOURCES',
      dayIndex: 1,
      ordinal: 3,
      status: 'PENDING',
      estimatedMinutes: 10,
      title: 'Reading section overview',
      resourceUrl: 'https://www.toeflresources.com/reading-section/',
    },
    {
      taskId: '30000000-0000-4000-8000-000000000006',
      kind: 'ITEM_SET',
      section: 'READING',
      taskType: 'COMPLETE_THE_WORDS',
      dayIndex: 4,
      ordinal: 1,
      status: options.rebalanced ? 'SKIPPED' : 'PENDING',
      skippedReason: options.rebalanced ? 'REBALANCE' : null,
      estimatedMinutes: 4,
      title: 'Complete the Words: Ocean currents',
      itemSetId: fixture('COMPLETE_THE_WORDS').id,
    },
  ];
  const state: PlanningStubState = { tasks, completions: [], finished: 0, practice };
  let version = 3;
  let weekStatus = 'IN_PROGRESS';
  const dayOffset = options.overdue ? -1 : 0; // Day 1 is yesterday when a task is overdue

  const counted = () => tasks.filter((task) => !(task.status === 'SKIPPED' && task.skippedReason === 'REBALANCE'));
  const progress = () => {
    const all = counted();
    return Math.round((100 * all.filter((task) => task.status === 'DONE').length) / all.length);
  };
  const currentDay = options.overdue ? 2 : 1;
  const wireTask = (task: Task): Json => ({
    weekId: WEEK_ID,
    provenance: task.kind === 'ITEM_SET' ? 'EXTRACTED' : null,
    legacy: false,
    skippedReason: null,
    lastAttemptId: null,
    completedAt: task.status === 'DONE' ? new Date().toISOString() : null,
    ...task,
    dayIndex: task.dayIndex === 0 ? 1 : task.dayIndex,
  });
  const week = (): Json => ({
    weekId: WEEK_ID,
    planId: PLAN_ID,
    userId: 'u1',
    weekNo: 1,
    status: weekStatus,
    startDate: isoDay(dayOffset),
    endDate: isoDay(dayOffset + 6),
    dayCount: 7,
    currentDayIndex: currentDay,
    progressPercent: progress(),
    mockExam: false,
    rationale: null,
    autoPublished: false,
    pendingSlots: 0,
    rebalance: {
      lastRebalancedAt: options.rebalanced ? new Date().toISOString() : null,
      nextAllowedAt: null,
      excludedTaskIds: tasks.filter((task) => task.skippedReason === 'REBALANCE').map((task) => task.taskId),
    },
    sections: ['READING', 'LISTENING', 'VOCABULARY', 'GRAMMAR', 'RESOURCES'].map((section) => ({
      section,
      tasks: tasks.filter((task) => task.section === section).map(wireTask),
    })),
    days: Array.from({ length: 7 }, (_, i) => ({
      dayIndex: i + 1,
      date: isoDay(dayOffset + i),
      estimatedMinutes: tasks.filter((task) => task.dayIndex === i + 1).reduce((s, t) => s + t.estimatedMinutes, 0),
      taskIds: tasks.filter((task) => task.dayIndex === i + 1).map((task) => task.taskId),
    })),
    version,
  });
  const summaries = (): Json => ({
    items: [1, 2, 3, 4].map((weekNo) => ({
      weekId: weekNo === 1 ? WEEK_ID : `20000000-0000-4000-8000-00000000000${weekNo}`,
      weekNo,
      status: weekNo === 1 ? weekStatus : 'DRAFT',
      startDate: isoDay(dayOffset + (weekNo - 1) * 7),
      endDate: isoDay(dayOffset + (weekNo - 1) * 7 + 6),
      progressPercent: weekNo === 1 ? progress() : null,
      mockExam: weekNo === 4,
      locked: false,
      finalScore: null,
    })),
  });
  const today = (): Json => {
    const overdue = tasks.filter((task) => task.dayIndex === 0 && task.status !== 'DONE');
    const todays = tasks.filter(
      (task) => task.dayIndex === currentDay - (options.overdue ? 1 : 0) && task.dayIndex > 0,
    );
    const open = (task: Task) => task.status === 'PENDING' || task.status === 'IN_PROGRESS';
    return {
      date: isoDay(0),
      weekId: WEEK_ID,
      weekNo: 1,
      dayIndex: currentDay,
      overdue: overdue.map((task) => ({ task: wireTask(task), overdueDays: 1 })),
      today: todays.map((task) => ({ task: wireTask(task) })),
      getAhead: tasks.filter((task) => task.dayIndex === 2 && open(task)).map((task) => ({ task: wireTask(task) })),
      vocabularyReview: { dueCount: 0 },
      waitingForTutor: false,
      estimatedMinutes: todays.reduce((sum, task) => sum + task.estimatedMinutes, 0),
      doneMinutes: todays.filter((task) => task.status === 'DONE').reduce((s, t) => s + t.estimatedMinutes, 0),
      goalReached: false,
    };
  };

  await page.route(`${API}/**`, async (route: Route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    const method = request.method();
    const json = (body: unknown, status = 200) =>
      route.fulfill({
        status,
        contentType: 'application/json',
        body: JSON.stringify(body),
        headers: {
          'access-control-allow-origin': request.headers()['origin'] ?? '*',
          'access-control-allow-credentials': 'true',
          'access-control-allow-headers': '*',
          'access-control-allow-methods': '*',
          'access-control-expose-headers': 'ETag',
          ETag: `"${version}"`,
        },
      });
    if (method === 'OPTIONS') return route.fallback();
    if (path === '/api/v1/me/study-goal') {
      return json({ overallTarget: 4.5, examBooked: true, examDate: isoDay(27), minutesPerDay: 60, revision: 1 });
    }
    if (path === '/api/v1/study-plans/current') {
      return json({
        planId: PLAN_ID,
        userId: 'u1',
        examDate: isoDay(27),
        timezone: 'Europe/Minsk',
        weeksCount: 4,
        minutesPerDay: 60,
        studyDays: 'ALL',
        startDate: isoDay(dayOffset),
        currentWeekNo: 1,
        daysToExam: 27,
        createdAt: new Date().toISOString(),
        version: 1,
      });
    }
    if (path === '/api/v1/study-plans/current/weeks') return json(summaries());
    if (path === `/api/v1/weeks/${WEEK_ID}` && method === 'GET') return json(week());
    if (path === `/api/v1/weeks/${WEEK_ID}/completion` && method === 'POST') {
      state.finished += 1;
      weekStatus = 'COMPLETED';
      version += 1;
      return json(week());
    }
    if (path === '/api/v1/today') return json(today());
    const completion = path.match(/^\/api\/v1\/week-tasks\/([^/]+)\/(completion|skip)$/);
    if (completion) {
      const task = tasks.find((candidate) => candidate.taskId === completion[1]);
      if (!task) return json({ title: 'not found' }, 404);
      if (completion[2] === 'skip') {
        task.status = 'SKIPPED';
        task.skippedReason = 'USER';
      } else if (method === 'POST') {
        task.status = 'DONE';
        state.completions.push(task.taskId);
      } else {
        task.status = 'PENDING';
      }
      version += 1;
      return json(wireTask(task));
    }
    if (path.endsWith('/submission') && method === 'POST') {
      // attempt-scored → WeekTaskCompleted in planning-service
      const task = tasks.find((candidate) => candidate.taskId === READING_TASK)!;
      task.status = 'DONE';
      task.lastAttemptId = practice.attemptId;
      version += 1;
    }
    if (path === '/api/v1/attempts' && method === 'POST') {
      const task = tasks.find((candidate) => candidate.taskId === READING_TASK)!;
      task.status = 'IN_PROGRESS';
    }
    return route.fallback();
  });

  return state;
}
