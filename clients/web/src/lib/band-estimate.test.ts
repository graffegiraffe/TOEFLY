import {
  formatBandRange,
  formatCefrRange,
  formatComparable,
  remainingHintKey,
  ringFraction,
  sectionLetter,
} from './band-estimate';

describe('band estimate', () => {
  it('AC-13.01.1 shows the overall range with CEFR and the comparable 0–120 score', () => {
    expect(formatBandRange(4, 4.5)).toBe('4.0–4.5');
    expect(formatBandRange(5, 5)).toBe('5.0');
    expect(formatCefrRange('B2', 'B2')).toBe('B2');
    expect(formatCefrRange('B2', 'C1')).toBe('B2 → C1');
    expect(formatCefrRange(null, 'C1')).toBe('C1');
    expect(formatCefrRange(null, null)).toBe('');
    expect(formatComparable(72, 83)).toBe('≈ 72–83 of 120');
    expect(formatComparable(95, 95)).toBe('≈ 95 of 120');
  });

  it('fills the ring by the band of six', () => {
    expect(ringFraction(3)).toBe(0.5);
    expect(ringFraction(6)).toBe(1);
    expect(ringFraction(null)).toBe(0);
    expect(ringFraction(9)).toBe(1);
  });

  it('AC-13.01.3 hints what is missing per section', () => {
    expect(remainingHintKey('READING', 12)).toBe('home.band.hint.questions');
    expect(remainingHintKey('WRITING', 2)).toBe('home.band.hint.essays');
    expect(remainingHintKey('SPEAKING', 2)).toBe('home.band.hint.speaking');
    expect(remainingHintKey('LISTENING', 0)).toBe('home.band.hint.wait');
    expect(sectionLetter('LISTENING')).toBe('L');
  });
});
