import {
  countWords,
  createPasteGuard,
  criterionKey,
  formatDelta,
  formatScore,
  matchesFilter,
  rangeOf,
  splitAnnotated,
  stepState,
  wordDiff,
} from './writing';
import { reviewComments } from '@/components/writing/comments';
import type { SubmissionVersion } from '@/api/generated/assessment';

describe('word counter', () => {
  it('AC-09.03.3 counts words separated by any whitespace and ignores bare punctuation', () => {
    expect(countWords('')).toBe(0);
    expect(countWords('   \n ')).toBe(0);
    expect(countWords('People live longer.')).toBe(3);
    expect(countWords('Hello,\n\nDr. Ortiz  —  I think so.')).toBe(6);
    expect(countWords("It's well-known: 42 cities")).toBe(4);
  });
});

describe('paste guard', () => {
  it('allows only text copied inside the editor', () => {
    const guard = createPasteGuard();
    expect(guard.allows('from the web')).toBe(false);
    guard.remember('my own sentence');
    expect(guard.allows('my own sentence')).toBe(true);
    expect(guard.allows('my own sentence\r\n')).toBe(true);
    expect(guard.allows('another text')).toBe(false);
    expect(guard.allows('')).toBe(true);
  });
});

describe('annotated segments', () => {
  const text = 'People lives longer because 😀 medicine is beter.';

  it('splits the text by UTF-16 offsets, including surrogate pairs', () => {
    const emoji = text.indexOf('😀');
    const beter = text.indexOf('beter');
    const segments = splitAnnotated(text, [
      { id: 'a', start: 7, end: 12, anchorQuote: 'lives' },
      { id: 'b', start: beter, end: beter + 5, anchorQuote: 'beter' },
    ]);
    expect(segments.map((segment) => segment.text).join('')).toBe(text);
    expect(segments.find((segment) => segment.ids.includes('a'))?.text).toBe('lives');
    expect(segments.find((segment) => segment.ids.includes('b'))?.text).toBe('beter');
    expect(segments.find((segment) => segment.ids.includes('b'))?.endsHere).toEqual(['b']);
    expect(emoji).toBeLessThan(beter);
    expect(text.slice(emoji, emoji + 2)).toBe('😀');
  });

  it('shares segments between overlapping annotations', () => {
    const segments = splitAnnotated('abcdef', [
      { id: 'x', start: 0, end: 4, anchorQuote: 'abcd' },
      { id: 'y', start: 2, end: 6, anchorQuote: 'cdef' },
    ]);
    expect(segments.map((segment) => [segment.text, segment.ids])).toEqual([
      ['ab', ['x']],
      ['cd', ['x', 'y']],
      ['ef', ['y']],
    ]);
  });

  it('treats a missing range as a general comment and repairs a range that misses its quote', () => {
    expect(rangeOf(text, { id: 'g', start: null, end: null, anchorQuote: '' })).toBeNull();
    expect(rangeOf(text, { id: 'm', start: 0, end: 3, anchorQuote: 'medicine' })).toEqual({
      id: 'm',
      start: text.indexOf('medicine'),
      end: text.indexOf('medicine') + 8,
    });
    expect(rangeOf(text, { id: 'o', start: 400, end: 410, anchorQuote: 'absent' })).toBeNull();
  });

  it('numbers anchored comments in the order of the text and lists general ones after them', () => {
    const annotation = (id: string, start: number | null, end: number | null, quote: string) => ({
      id,
      start,
      end,
      anchorQuote: quote,
      category: 'GRAMMAR' as const,
      subcategory: 'GRAMMAR.AGREEMENT',
      severity: 'MAJOR' as const,
      message: 'm',
      status: 'OPEN' as const,
    });
    const version = {
      versionNo: 1,
      text,
      evaluation: {
        annotations: [
          annotation('late', text.indexOf('beter'), text.indexOf('beter') + 5, 'beter'),
          annotation('general', null, null, ''),
          annotation('early', 7, 12, 'lives'),
        ],
      },
    } as unknown as SubmissionVersion;
    expect(reviewComments(version).map((comment) => [comment.id, comment.number])).toEqual([
      ['early', 1],
      ['late', 2],
      ['general', undefined],
    ]);
  });
});

describe('word diff', () => {
  it('AC-11.05.2 marks removed and added words and keeps the rest', () => {
    expect(wordDiff('People lives longer now.', 'People live much longer now.')).toEqual([
      { type: 'same', text: 'People ' },
      { type: 'removed', text: 'lives ' },
      { type: 'added', text: 'live much ' },
      { type: 'same', text: 'longer now.' },
    ]);
  });

  it('handles empty texts', () => {
    expect(wordDiff('', 'New text')).toEqual([{ type: 'added', text: 'New text' }]);
    expect(wordDiff('Old', '')).toEqual([{ type: 'removed', text: 'Old' }]);
    expect(wordDiff('', '')).toEqual([]);
  });
});

describe('rubric score', () => {
  it('formats scores with a step of 0.5 and the change since the previous version', () => {
    expect(formatScore(4)).toBe('4');
    expect(formatScore(3.5)).toBe('3.5');
    expect(formatDelta(1)).toBe('+1');
    expect(formatDelta(-0.5)).toBe('−0.5');
    expect(formatDelta(0)).toBe('±0');
  });

  it('lights the division of the score, and both neighbours of a half score', () => {
    expect([0, 1, 2, 3, 4, 5].map((step) => stepState(step, 4))).toEqual(['off', 'off', 'off', 'off', 'active', 'off']);
    expect([0, 1, 2, 3, 4, 5].map((step) => stepState(step, 3.5))).toEqual([
      'off',
      'off',
      'off',
      'half',
      'half',
      'off',
    ]);
  });

  it('names conventions "Contribution" for an academic discussion', () => {
    expect(criterionKey('conventions', 'WRITE_ACADEMIC_DISCUSSION')).toBe('contribution');
    expect(criterionKey('conventions', 'WRITE_EMAIL')).toBe('conventions');
    expect(criterionKey('taskAchievement', 'WRITE_EMAIL')).toBe('taskAchievement');
    expect(criterionKey('mystery', 'WRITE_EMAIL')).toBe('other');
  });

  it('filters comments by category; Coherence also covers task response', () => {
    expect(matchesFilter('ALL', { category: 'SPELLING' })).toBe(true);
    expect(matchesFilter('GRAMMAR', { category: 'SPELLING' })).toBe(false);
    expect(matchesFilter('COHERENCE', { category: 'TASK_RESPONSE' })).toBe(true);
    expect(matchesFilter('TUTOR', { category: 'GRAMMAR', byTutor: true })).toBe(true);
    expect(matchesFilter('TUTOR', { category: 'GRAMMAR' })).toBe(false);
  });
});
