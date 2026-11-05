import { afterEach, describe, expect, it } from 'vitest';
import {
  applyReduceTransparency,
  goalChangeAppliesNextWeek,
  isDark,
  settingsTabsFor,
  storedReduceTransparency,
  storedTheme,
} from './settings';

describe('docs/07 §5.19 settings tabs', () => {
  it('learners see goal and schedule, tutors without STUDENT do not', () => {
    expect(settingsTabsFor(['STUDENT'])).toEqual([
      'profile',
      'goal',
      'schedule',
      'learning',
      'appearance',
      'privacy',
      'account',
    ]);
    expect(settingsTabsFor(['TUTOR'])).not.toContain('goal');
    expect(settingsTabsFor(['TUTOR'])).not.toContain('schedule');
  });
});

describe('Settings → Appearance', () => {
  afterEach(() => localStorage.clear());

  it('System follows the operating system, Light and Dark override it', () => {
    expect(isDark('system', true)).toBe(true);
    expect(isDark('system', false)).toBe(false);
    expect(isDark('light', true)).toBe(false);
    expect(isDark('dark', false)).toBe(true);
  });

  it('System is the default theme', () => {
    expect(storedTheme()).toBe('system');
  });

  it('reduce transparency marks the document and is remembered', () => {
    applyReduceTransparency(true);
    expect(document.documentElement.dataset.transparency).toBe('reduced');
    expect(storedReduceTransparency()).toBe(true);
    applyReduceTransparency(false);
    expect(document.documentElement.dataset.transparency).toBeUndefined();
    expect(storedReduceTransparency()).toBe(false);
  });
});

describe('AC-02.05.1 changes apply from next week', () => {
  it('a new exam date or daily minutes apply from next week', () => {
    const before = { examDate: '2027-01-10', minutesPerDay: 60 };
    expect(goalChangeAppliesNextWeek(before, { examDate: '2027-02-01', minutesPerDay: 60 })).toBe(true);
    expect(goalChangeAppliesNextWeek(before, { examDate: '2027-01-10', minutesPerDay: 90 })).toBe(true);
    expect(goalChangeAppliesNextWeek(before, { examDate: null, minutesPerDay: 60 })).toBe(true);
  });

  it('a new target alone changes nothing about the weeks', () => {
    const before = { examDate: '2027-01-10', minutesPerDay: 60 };
    expect(goalChangeAppliesNextWeek(before, { ...before })).toBe(false);
  });
});
