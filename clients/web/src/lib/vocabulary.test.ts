import { describe, expect, it } from 'vitest';
import {
  ReviewQueue,
  clozeOf,
  formatInterval,
  nextInLabel,
  selectionTerm,
  sentenceAround,
  stabilityLevel,
} from './vocabulary';

describe('AC-15.03.2 intervals under the grade buttons', () => {
  it('shows minutes, hours, days, months and years', () => {
    expect(formatInterval(60)).toBe('1m');
    expect(formatInterval(330)).toBe('6m');
    expect(formatInterval(600)).toBe('10m');
    expect(formatInterval(3 * 3600)).toBe('3h');
    expect(formatInterval(86_400)).toBe('1d');
    expect(formatInterval(16 * 86_400)).toBe('16d');
    expect(formatInterval(61 * 86_400)).toBe('2mo');
    expect(formatInterval(365 * 86_400)).toBe('1y');
    expect(formatInterval(0)).toBe('now');
  });

  it('says when the next card is due after the session', () => {
    expect(nextInLabel(6 * 3600)).toBe('6 h');
    expect(nextInLabel(40 * 60)).toBe('40 min');
    expect(nextInLabel(3 * 86_400)).toBe('3 days');
    expect(nextInLabel(86_400)).toBe('1 day');
  });
});

describe('docs/07 §5.14 stability indicator (4 dots)', () => {
  it('grows with the FSRS stability; Mastered fills all four', () => {
    expect(stabilityLevel('NEW', 0)).toBe(0);
    expect(stabilityLevel('LEARNING', 0.4)).toBe(1);
    expect(stabilityLevel('REVIEW', 4)).toBe(2);
    expect(stabilityLevel('REVIEW', 45)).toBe(3);
    expect(stabilityLevel('MASTERED', 120)).toBe(4);
  });
});

describe('AC-15.03.2 the cloze format hides the word in its sentence', () => {
  it('finds the lemma and its inflected form, case-insensitively', () => {
    expect(clozeOf('Wetlands mitigate floods.', 'mitigate')).toEqual({ before: 'Wetlands ', after: ' floods.' });
    expect(clozeOf('It mitigated the damage.', 'mitigate')).toEqual({ before: 'It ', after: ' the damage.' });
    expect(clozeOf('Allocate the funds.', 'allocate')).toEqual({ before: '', after: ' the funds.' });
    expect(clozeOf('Nothing here.', 'mitigate')).toBeNull();
    expect(clozeOf(null, 'mitigate')).toBeNull();
  });
});

describe('AC-15.01.1 a selection becomes a term with its sentence', () => {
  it('accepts one to four words and trims punctuation', () => {
    expect(selectionTerm('  canopy, ')).toBe('canopy');
    expect(selectionTerm('“heat island”')).toBe('heat island');
    expect(selectionTerm('carry it out now please')).toBeNull();
    expect(selectionTerm('1984')).toBeNull();
    expect(selectionTerm('')).toBeNull();
    expect(selectionTerm('a'.repeat(81))).toBeNull();
  });

  it('takes the sentence of the block that contains the word', () => {
    const text = 'Urban forests mitigate heat. A dense canopy shades the streets! Leaves cool the air.';
    expect(sentenceAround(text, 'canopy')).toBe('A dense canopy shades the streets!');
    expect(sentenceAround(text, 'missing')).toBeUndefined();
    expect(sentenceAround('x '.repeat(400) + 'canopy.', 'canopy')).toHaveLength(300);
  });
});

describe('AC-15.03.5 the session queue brings Again back', () => {
  const cards = ['a', 'b', 'c'].map((id) => ({ id }));

  it('shows cards in order, requeues a learning card and counts the distinct cards', () => {
    const queue = new ReviewQueue(cards);
    expect(queue.current()?.id).toBe('a');
    expect(queue.progress()).toEqual({ done: 0, total: 3 });

    queue.answer({ id: 'a' }, { learning: true, dueInSeconds: 60 }, 0);
    expect(queue.current(1_000)?.id).toBe('b');
    expect(queue.progress()).toEqual({ done: 1, total: 3 });
    queue.answer({ id: 'b' }, { learning: false, dueInSeconds: 86_400 }, 1_000);
    queue.answer({ id: 'c' }, { learning: false, dueInSeconds: 86_400 }, 2_000);

    // only "a" is left: shown again even before its minute is over (learn ahead)
    expect(queue.current(3_000)?.id).toBe('a');
    expect(queue.finished()).toBe(false);
    queue.answer({ id: 'a' }, { learning: false, dueInSeconds: 86_400 }, 3_000);
    expect(queue.finished()).toBe(true);
    expect(queue.progress()).toEqual({ done: 3, total: 3 });
  });

  it('numbers every showing for the idempotent grade', () => {
    const queue = new ReviewQueue(cards);
    expect(queue.sequence()).toBe(0);
    queue.answer({ id: 'a' }, { learning: true, dueInSeconds: 60 }, 0);
    expect(queue.sequence()).toBe(1);
  });

  it('prefers a card whose time has come over learning ahead', () => {
    const queue = new ReviewQueue([{ id: 'a' }, { id: 'b' }]);
    queue.answer({ id: 'a' }, { learning: true, dueInSeconds: 60 }, 0);
    queue.answer({ id: 'b' }, { learning: true, dueInSeconds: 600 }, 1_000);
    expect(queue.current(30_000)?.id).toBe('a');
    expect(queue.current(61_000)?.id).toBe('a');
  });

  it('an empty session is finished at once (AC-15.03.4)', () => {
    expect(new ReviewQueue([]).finished()).toBe(true);
  });
});
