import { describe, expect, it } from 'vitest';
import { answerText, fromApi, isAnswered, sameAnswer, toApi, type Answer } from './answers';
import { emptySlots, place, placeInFirstFree, pool, remove, slotsFrom } from './chunks';
import { acceptsLetter, cellsOf, parseCloze, textOf } from './cloze';
import { paragraphPieces, splitSentences } from './passage';

describe('US-07.01 Complete the Words', () => {
  it('AC-07.01.1: the markup becomes text and gaps with their visible first part', () => {
    const segments = parseCloze('Rivers shape the land. Ov{_} wa{_} aw{_}. Rivers keep moving.');
    expect(segments).toEqual([
      { kind: 'text', text: 'Rivers shape the land. ' },
      { kind: 'gap', index: 0, prefix: 'Ov' },
      { kind: 'text', text: ' ' },
      { kind: 'gap', index: 1, prefix: 'wa' },
      { kind: 'text', text: ' ' },
      { kind: 'gap', index: 2, prefix: 'aw' },
      { kind: 'text', text: '. Rivers keep moving.' },
    ]);
  });

  it('AC-07.01.3: a cell takes only Latin letters, an apostrophe and a hyphen', () => {
    expect(['a', 'Z', "'", '-'].every(acceptsLetter)).toBe(true);
    expect(['1', 'я', ' ', 'ab', 'Enter'].some(acceptsLetter)).toBe(false);
  });

  it('AC-07.01.2: letters keep their cells, empty cells in the middle stay empty', () => {
    expect(cellsOf('te r', 4)).toEqual(['t', 'e', '', 'r']);
    expect(textOf(['t', 'e', '', 'r'])).toBe('te r');
    expect(textOf(['t', '', '', ''])).toBe('t');
    expect(cellsOf('', 2)).toEqual(['', '']);
  });
});

describe('US-09.01 Build a Sentence', () => {
  const chunks = ['c1', 'c2', 'c3', 'c4'];

  it('AC-09.01.3: a tap puts a fragment into the first free slot and takes it back', () => {
    let slots = emptySlots(3);
    slots = placeInFirstFree(slots, 'c2');
    slots = placeInFirstFree(slots, 'c3');
    expect(slots).toEqual(['c2', 'c3', null]);
    expect(pool(chunks, slots)).toEqual(['c1', 'c4']);
    slots = remove(slots, 'c2');
    expect(slots).toEqual([null, 'c3', null]);
    expect(placeInFirstFree(['c1', 'c2', 'c3'], 'c4')).toEqual(['c1', 'c2', 'c3']);
  });

  it('AC-09.01.2: dropping on a taken slot swaps, dropping from the pool sends the occupant back', () => {
    expect(place(['c1', 'c2', null], 'c1', 1)).toEqual(['c2', 'c1', null]);
    expect(place(['c1', 'c2', null], 'c4', 0)).toEqual(['c4', 'c2', null]);
    expect(place(['c1', null], 'c1', 0)).toEqual(['c1', null]);
    expect(place(['c1', null], 'c2', 5)).toEqual(['c1', null]);
    expect(slotsFrom(['c1', null, 'c3', 'c4'], 3)).toEqual(['c1', null, 'c3']);
  });
});

describe('US-07.03 Academic Passage', () => {
  it('splits a paragraph into sentences', () => {
    expect(splitSentences('Reefs grow. Is it slow? Yes! "Very." End')).toEqual([
      'Reefs grow.',
      'Is it slow?',
      'Yes!',
      '"Very."',
      'End',
    ]);
  });

  it('AC-07.03.3: markers stand after their sentence; a marker past the end stays visible', () => {
    const pieces = paragraphPieces('One. Two.', 0, [
      { key: 'A', paragraph: 0, afterSentence: 0 },
      { key: 'B', paragraph: 0, afterSentence: 4 },
      { key: 'C', paragraph: 1, afterSentence: 0 },
    ]);
    expect(pieces).toEqual([
      { kind: 'text', text: 'One.' },
      { kind: 'marker', key: 'A' },
      { kind: 'text', text: ' Two.' },
      { kind: 'marker', key: 'B' },
    ]);
  });

  it('AC-07.03.2: the target word is highlighted', () => {
    const pieces = paragraphPieces('Coral reefs grow slowly.', 0, [], { paragraph: 0, charStart: 12, charEnd: 16 });
    expect(pieces).toEqual([
      { kind: 'text', text: 'Coral reefs ' },
      { kind: 'text', text: 'grow', highlight: true },
      { kind: 'text', text: ' slowly.' },
    ]);
    expect(paragraphPieces('Other.', 1, [], { paragraph: 0, charStart: 0, charEnd: 2 })).toEqual([
      { kind: 'text', text: 'Other.' },
    ]);
  });
});

describe('answers', () => {
  it('round-trips every answer kind through the API shape', () => {
    const answers: Answer[] = [
      { type: 'CHOICE', optionIds: ['A', 'C'] },
      { type: 'TEXT', text: 'er' },
      { type: 'SEQUENCE', fragmentIds: ['c1', null] },
      { type: 'MAPPING', pairs: [{ key: 'B', value: 'went' }] },
    ];
    for (const answer of answers) {
      expect(fromApi(toApi(answer))).toEqual(answer);
    }
    expect(fromApi(null)).toBeNull();
  });

  it('AC-06.01.5: empty answers do not count as answered', () => {
    expect(isAnswered({ type: 'CHOICE', optionIds: [] })).toBe(false);
    expect(isAnswered({ type: 'TEXT', text: '  ' })).toBe(false);
    expect(isAnswered({ type: 'SEQUENCE', fragmentIds: [null, null] })).toBe(false);
    expect(isAnswered({ type: 'MAPPING', pairs: [{ key: 'A', value: '' }] })).toBe(false);
    expect(isAnswered(null)).toBe(false);
    expect(isAnswered({ type: 'SEQUENCE', fragmentIds: [null, 'c1'] })).toBe(true);
  });

  it('describes answers for the review', () => {
    expect(answerText({ type: 'SEQUENCE', fragmentIds: ['c1', null] }, (id) => `<${id}>`)).toBe('<c1> ___');
    expect(answerText({ type: 'CHOICE', optionIds: ['A', 'C'] })).toBe('A, C');
    expect(answerText({ type: 'MAPPING', pairs: [{ key: 'B', value: 'went' }] })).toBe('went');
    expect(answerText({ type: 'TEXT', text: 'er' })).toBe('er');
    expect(answerText(null)).toBe('');
    expect(sameAnswer({ type: 'TEXT', text: 'a' }, { type: 'TEXT', text: 'a' })).toBe(true);
  });
});
