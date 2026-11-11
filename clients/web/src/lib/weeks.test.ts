import type { Week, WeekSummary, WeekTask } from '@/api/generated/planning';
import { isComposed, weekIdOf } from '@/api/planning';
import { parseDate } from '@/api/generated/planning/runtime';
import { sectionCoverId, weekCoverId } from './covers';
import { counted, dateOf, firstOpen, formatRange, sectionFromSlug, sectionProgress, taskLink, tasksOf } from './weeks';

const task = (overrides: Record<string, unknown>): WeekTask =>
  ({
    taskId: crypto.randomUUID(),
    weekId: 'w',
    kind: 'ITEM_SET',
    section: 'READING',
    dayIndex: 1,
    ordinal: 1,
    status: 'PENDING',
    estimatedMinutes: 3,
    title: 'Task',
    ...overrides,
  }) as WeekTask;

const week = (tasks: WeekTask[]): Week =>
  ({
    weekId: 'w',
    planId: 'p',
    userId: 'u',
    weekNo: 2,
    status: 'IN_PROGRESS',
    startDate: parseDate('2026-11-10'),
    endDate: parseDate('2026-11-16'),
    dayCount: 7,
    progressPercent: 0,
    sections: [
      { section: 'READING', tasks: tasks.filter((t) => t.section === 'READING') },
      { section: 'GRAMMAR', tasks: tasks.filter((t) => t.section === 'GRAMMAR') },
    ],
    days: [],
    version: 1,
  }) as unknown as Week;

describe('weeks', () => {
  it('AC-04.02.1 tasks of a section follow the days', () => {
    const w = week([task({ dayIndex: 3, title: 'b' }), task({ dayIndex: 1, title: 'a' })]);
    expect(tasksOf(w, 'READING').map((t) => t.title)).toEqual(['a', 'b']);
  });

  it('AC-04.02.3 tasks excluded by a rebalance leave the section total, a skip by the learner stays', () => {
    const tasks = [
      task({ status: 'DONE' }),
      task({ status: 'SKIPPED', skippedReason: 'REBALANCE' }),
      task({ status: 'SKIPPED', skippedReason: 'USER_SKIPPED' }),
    ];
    expect(counted(tasks)).toHaveLength(2);
    expect(sectionProgress(week(tasks), 'READING')).toEqual({ done: 1, total: 2 });
  });

  // The client parses a date as local midnight; the days hold in every zone (TZ=Pacific/Kiritimati, Etc/GMT+12).
  it('dates are the local days of the week', () => {
    const w = week([]);
    expect(dateOf(w, 1).getDate()).toBe(10);
    expect(dateOf(w, 7).getDate()).toBe(16);
    expect(formatRange(parseDate('2026-11-10'), parseDate('2026-11-16'))).toBe('Nov 10 – Nov 16, 2026');
  });

  it('a task opens the player, its link or the section page', () => {
    expect(taskLink(task({})).kind).toBe('player');
    expect(taskLink(task({ kind: 'EXTERNAL_RESOURCE', resourceUrl: 'https://x.test' })).kind).toBe('external');
    expect(taskLink(task({ kind: 'GRAMMAR_UNIT', section: 'GRAMMAR' })).kind).toBe('section');
  });

  it('the Start button takes the first open task', () => {
    expect(firstOpen([task({ status: 'DONE', title: 'a' }), task({ title: 'b' })])?.title).toBe('b');
    expect(firstOpen([task({ status: 'DONE' })])).toBeUndefined();
  });

  it('section slugs map back to sections', () => {
    expect(sectionFromSlug('listening')).toBe('LISTENING');
    expect(sectionFromSlug('drawing')).toBeUndefined();
  });

  it('decision 9: the week number finds a composed week only', () => {
    const weeks = [
      { weekId: 'a', weekNo: 1, status: 'IN_PROGRESS' },
      { weekId: 'b', weekNo: 2, status: 'DRAFT' },
    ] as WeekSummary[];
    expect(weekIdOf(weeks, 1)).toBe('a');
    expect(weekIdOf(weeks, 2)).toBeUndefined();
    expect(isComposed(weeks[1] as WeekSummary)).toBe(false);
  });

  it('docs/07 §8: mock exam weeks get the exam desk, the others rotate', () => {
    expect(weekCoverId(4, true)).toBe('week-04-exam-desk');
    expect(weekCoverId(4, false)).toBe('week-05-headphones');
    expect(weekCoverId(17, false)).toBe('week-01-campus');
    expect(sectionCoverId('GRAMMAR')).toBe('section-grammar');
  });
});
