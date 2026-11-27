import { describe, expect, it } from 'vitest';
import { bandDelta, bandRows, formatMinutes, scoreOf, sectionPercent, subcategoryTitle } from './weekly-review';

describe('weekly review figures (AC-18.01.1)', () => {
  it('formats study time as hours and minutes', () => {
    expect(formatMinutes(250)).toBe('4 h 10 min');
    expect(formatMinutes(45)).toBe('45 min');
    expect(formatMinutes(120)).toBe('2 h');
  });

  it('gives the done share of a section', () => {
    expect(
      sectionPercent({ section: 'READING', tasksDone: 3, tasksTotal: 4, minutesDone: 40, minutesPlanned: 50 }),
    ).toBe(75);
    expect(sectionPercent({ section: 'READING', tasksDone: 0, tasksTotal: 0, minutesDone: 0, minutesPlanned: 0 })).toBe(
      0,
    );
  });

  it('labels the band change with a sign, none for no change', () => {
    expect(bandDelta(0.5)).toBe('+0.5');
    expect(bandDelta(-0.5)).toBe('−0.5');
    expect(bandDelta(0)).toBeNull();
    expect(bandDelta(null)).toBeNull();
  });

  it('lists only the sections of the snapshot with a value', () => {
    expect(bandRows({ overall: 4, reading: 4.5, listening: null, writing: 3.5 })).toEqual([
      { section: 'reading', value: 4.5 },
      { section: 'writing', value: 3.5 },
    ]);
  });

  it('shows accuracy for closed tasks and the 0–5 average for Writing', () => {
    expect(scoreOf({ taskType: 'READ_DAILY_LIFE', attempts: 2, accuracyPercent: 83.3 })).toEqual({
      kind: 'accuracy',
      value: '83',
    });
    expect(scoreOf({ taskType: 'WRITE_EMAIL', attempts: 1, averageScore: 3.5 })).toEqual({
      kind: 'average',
      value: '3.5',
    });
    expect(scoreOf({ taskType: 'WRITE_EMAIL', attempts: 0 })).toBeNull();
  });

  it('turns a subcategory code into words', () => {
    expect(subcategoryTitle('GRAMMAR.SUBJECT_VERB_AGREEMENT')).toBe('Subject verb agreement');
    expect(subcategoryTitle('GRAMMAR')).toBe('Grammar');
  });
});
