import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactNode } from 'react';
import { describe, expect, it, vi } from 'vitest';
import '@/i18n';
import { ExplanationLanguage, type WeeklyReview } from '@/api/generated/tutoragent';

vi.mock('@tanstack/react-router', () => ({
  Link: ({ children, params }: { children: ReactNode; params?: { weekNumber?: string } }) => (
    <a href={`/weeks/${params?.weekNumber ?? ''}`}>{children}</a>
  ),
}));

import { WeeklyReviewView } from './weekly-review-view';

const REVIEW: WeeklyReview = {
  reviewId: '70000000-0000-4000-8000-000000000001',
  weekId: '20000000-0000-4000-8000-000000000001',
  weekNo: 1,
  generatedAt: new Date('2026-11-22T10:00:00Z'),
  language: ExplanationLanguage.En,
  aiFallback: false,
  lowActivity: false,
  headline: 'Reading is up, articles still slip',
  summary: 'You did 5 of 6 tasks in 2 h 05 min. Reading accuracy rose to 83%.',
  stats: {
    tasksTotal: 6,
    tasksDone: 5,
    progressPercent: 83,
    activeMinutes: 125,
    sections: [
      { section: 'READING', tasksTotal: 2, tasksDone: 2, minutesPlanned: 30, minutesDone: 34 },
      { section: 'WRITING', tasksTotal: 2, tasksDone: 1, minutesPlanned: 20, minutesDone: 12 },
    ],
  },
  scores: [
    { taskType: 'READ_DAILY_LIFE', attempts: 2, accuracyPercent: 83.3 },
    { taskType: 'WRITE_ACADEMIC_DISCUSSION', attempts: 1, averageScore: 3.5 },
  ],
  bandSnapshot: { overall: 4, reading: 4.5, writing: 3.5, overallDelta: 0.5 },
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
  ],
  strengths: ['Reading: you find the main idea fast.', 'You studied five days in a row.'],
  nextWeekFocus: [{ subcategory: 'GRAMMAR.ARTICLES', why: 'Four slips this week.' }],
  motivation: 'One more steady week and articles will stop costing you points.',
  tutorNote: null,
};

describe('Weekly review page (US-18.01)', () => {
  it('AC-18.01.1, AC-18.01.2 shows every block and the link to the next week', async () => {
    const ask = vi.fn();
    render(<WeeklyReviewView review={REVIEW} nextWeekReady onAskTutor={ask} />);
    expect(screen.getByTestId('review-headline')).toHaveTextContent('Reading is up, articles still slip');
    expect(screen.getByText(/Reading accuracy rose to 83%/)).toBeInTheDocument();

    const did = screen.getByRole('region', { name: 'What you did' });
    expect(within(did).getByTestId('review-done')).toHaveTextContent('5 of 6 tasks · 2 h 5 min');
    expect(within(did).getByText(/1 of 2 · 12 of 20 min/)).toBeInTheDocument();
    expect(within(did).getAllByRole('progressbar')).toHaveLength(2);

    const scores = screen.getByRole('region', { name: 'Scores' });
    expect(within(scores).getByText('83% correct')).toBeInTheDocument();
    expect(within(scores).getByText('3.5 of 5')).toBeInTheDocument();
    expect(within(scores).getByText('AI estimate')).toBeInTheDocument();
    expect(within(scores).getByTestId('band-delta')).toHaveTextContent('+0.5 this week');

    const mistakes = screen.getByRole('region', { name: 'Top recurring mistakes' });
    expect(within(mistakes).getByText('Missing "the" before a known noun')).toBeInTheDocument();
    expect(within(mistakes).getByText('4 times')).toBeInTheDocument();
    expect(within(mistakes).getByText('I went to library').tagName).toBe('DEL');
    expect(within(mistakes).getByText('I went to the library').tagName).toBe('INS');
    expect(within(mistakes).getByText('Review: ROUNDUP5.U3 — Articles')).toBeInTheDocument();

    expect(screen.getByRole('region', { name: 'Strengths' })).toHaveTextContent('you find the main idea fast');
    expect(screen.getByRole('region', { name: 'Focus for Week 2' })).toHaveTextContent(
      'Articles: Four slips this week.',
    );
    expect(screen.getByTestId('review-motivation')).toHaveTextContent('One more steady week');
    expect(screen.getByRole('link', { name: 'See Week 2' })).toHaveAttribute('href', '/weeks/2');

    await userEvent.setup().click(screen.getByRole('button', { name: 'Ask tutor' }));
    expect(ask).toHaveBeenCalled();
  });

  it('AC-18.01.2 next week not built yet', () => {
    render(<WeeklyReviewView review={REVIEW} nextWeekReady={false} />);
    expect(screen.getByText('Week 2 is being prepared')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /See Week/ })).not.toBeInTheDocument();
  });

  it('AC-18.01.5 a week with little activity has no list of mistakes', () => {
    render(
      <WeeklyReviewView
        review={{ ...REVIEW, lowActivity: true, topMistakes: [], headline: 'A quiet week' }}
        nextWeekReady={false}
      />,
    );
    expect(screen.getByTestId('low-activity')).toHaveTextContent('Not enough activity this week');
    expect(screen.queryByRole('region', { name: 'Top recurring mistakes' })).not.toBeInTheDocument();
  });

  it('a Russian review marks its AI text as Russian; the tutor note comes first', () => {
    render(
      <WeeklyReviewView
        review={{
          ...REVIEW,
          language: ExplanationLanguage.Ru,
          headline: 'Reading растёт, артикли пока подводят',
          tutorNote: 'Great week. Let us fix the articles.',
        }}
        nextWeekReady
      />,
    );
    expect(screen.getByTestId('review-headline').closest('[lang]')).toHaveAttribute('lang', 'ru');
    const regions = screen.getAllByRole('region').map((region) => region.getAttribute('aria-labelledby'));
    expect(regions[0]).toBe('review-tutor');
  });
});
