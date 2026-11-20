import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import '@/i18n';
import { PaceVerdict, type Section, type Streak, type TaskType } from '@/api/generated/analytics';
import { dailyBars } from '@/lib/analytics';
import { ActivityChart } from './activity-chart';
import { BandChart } from './band-chart';
import { Freshness } from './freshness';
import { PaceTable } from './pace-table';
import { StreakCard } from './streak-card';

const day = (value: string) => new Date(`${value}T00:00:00Z`);

function streak(over: Partial<Streak> = {}): Streak {
  const days = Array.from({ length: 30 }, (_, i) => ({
    date: day(`2026-11-${String(i + 1).padStart(2, '0')}`),
    done: i >= 27,
  }));
  return {
    current: 3,
    longest: 8,
    todayDone: true,
    goalMinutes: 30,
    today: day('2026-11-30'),
    timezone: 'Europe/Minsk',
    todayActiveSeconds: 1980,
    todayGoalProgress: 1,
    days,
    asOf: new Date('2026-11-30T10:00:00Z'),
    ...over,
  };
}

describe('StreakCard', () => {
  it('AC-19.01.2, AC-19.01.3 shows the current and record streak, "Today done" and a 30-day calendar', () => {
    render(<StreakCard streak={streak()} />);

    expect(screen.getByTestId('streak-current')).toHaveTextContent('3 days');
    expect(screen.getByText(/Record 8 days/)).toBeInTheDocument();
    expect(screen.getByTestId('today-done')).toHaveTextContent('Today done');
    const calendar = screen.getByRole('list', { name: 'Last 30 days' });
    expect(within(calendar).getAllByRole('listitem')).toHaveLength(30);
    expect(within(calendar).getAllByLabelText(/goal met/)).toHaveLength(3);
  });

  it('AC-19.02.2 the goal ring shows the share of the daily goal; compact hides the calendar', () => {
    render(<StreakCard streak={streak({ todayDone: false, todayGoalProgress: 0.4, current: 1 })} compact />);

    expect(screen.getByRole('progressbar', { name: 'Daily goal' })).toHaveAttribute('aria-valuenow', '40');
    expect(screen.queryByTestId('today-done')).not.toBeInTheDocument();
    expect(screen.queryByTestId('streak-calendar')).not.toBeInTheDocument();
    expect(screen.getByTestId('streak-current')).toHaveTextContent('1 day');
  });
});

describe('Freshness', () => {
  it('AC-05.02.3 says how fresh the read model is', () => {
    render(<Freshness asOf={new Date('2026-11-30T09:57:00Z')} now={new Date('2026-11-30T10:00:00Z')} />);
    expect(screen.getByTestId('freshness')).toHaveTextContent('Updated 3 minutes ago');
  });
});

describe('ActivityChart', () => {
  it('AC-16.02.1 one bar per day by section, with the table alternative (WCAG 1.1.1)', async () => {
    const bars = dailyBars(
      [
        {
          date: day('2026-11-28'),
          activeSeconds: 1800,
          goalMet: true,
          bySection: [
            { section: 'READING', activeSeconds: 1200 },
            { section: 'GRAMMAR', activeSeconds: 600 },
          ],
        },
      ],
      day('2026-11-24'),
      day('2026-12-01'),
    );
    render(<ActivityChart bars={bars} goalMinutes={30} />);

    expect(screen.getByRole('list', { name: 'Weekly activity' }).children).toHaveLength(7);
    expect(screen.getByLabelText(/: 30 m$/)).toBeInTheDocument();
    expect(screen.getByText('Goal 30 min')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Show as table' }));
    const table = screen.getByRole('table');
    expect(within(table).getByText('Reading 20 m, Grammar 10 m')).toBeInTheDocument();
    expect(within(table).getByText('Met')).toBeInTheDocument();
  });
});

describe('BandChart', () => {
  const weeks = [
    {
      weekNo: 1,
      progressPercent: 80,
      tasksDone: 14,
      tasksTotal: 18,
      completed: true,
      overall: { point: 4, low: 3.5, high: 4.5 },
      sections: [{ section: 'READING' as Section, point: 4.5 }],
    },
    { weekNo: 2, progressPercent: 30, tasksDone: 5, tasksTotal: 18, completed: false, overall: null, sections: [] },
  ];

  it('AC-13.02.1, AC-13.02.3 draws the band with the target and offers a table; AC-13.02.2 a week without data', async () => {
    render(<BandChart weeks={weeks} target={5} />);

    expect(screen.getByRole('img', { name: /Overall band estimate/ })).toBeInTheDocument();
    expect(screen.getByTestId('band-target')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Reading' }));
    expect(screen.getByRole('button', { name: 'Reading' })).toHaveAttribute('aria-pressed', 'true');

    await userEvent.click(screen.getByRole('button', { name: 'Show as table' }));
    const rows = within(screen.getByRole('table')).getAllByRole('row');
    expect(rows[1]).toHaveTextContent('W13.5–4.54.5———80%');
    expect(rows[2]).toHaveTextContent('Need more data');
  });

  it('shows an empty state before the first week', () => {
    render(<BandChart weeks={[]} />);
    expect(screen.getByText(/once your first week starts/)).toBeInTheDocument();
  });
});

describe('PaceTable', () => {
  it('AC-16.02.2 highlights a deviation from the exam pace over 30%', () => {
    render(
      <PaceTable
        rows={[
          {
            taskType: 'READ_ACADEMIC_PASSAGE' as TaskType,
            activeSeconds: 522,
            normSeconds: 360,
            paceRatio: 1.45,
            verdict: PaceVerdict.Slower,
            attempts: 1,
            slowAttempts: 1,
            fastAttempts: 0,
          },
          {
            taskType: 'GRAMMAR_EXERCISE' as TaskType,
            activeSeconds: 300,
            normSeconds: 0,
            paceRatio: null,
            verdict: PaceVerdict.NoNorm,
            attempts: 0,
            slowAttempts: 0,
            fastAttempts: 0,
          },
        ]}
      />,
    );

    expect(screen.getByText('Read an Academic Passage')).toBeInTheDocument();
    expect(screen.getByText('45% slower')).toBeInTheDocument();
    expect(screen.getByText('No norm')).toBeInTheDocument();
  });

  it('explains how to fill an empty report', () => {
    render(<PaceTable rows={[]} />);
    expect(screen.getByText(/compare your pace/)).toBeInTheDocument();
  });
});
