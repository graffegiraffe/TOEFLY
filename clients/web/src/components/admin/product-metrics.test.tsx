import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import '@/i18n';

const getProductMetrics = vi.fn();
const getAiUsageReport = vi.fn();
const rebuildProjection = vi.fn();
const getProjectionRebuild = vi.fn();
vi.mock('@/api/client', () => ({
  api: {
    analyticsAdmin: {
      getProductMetrics: (...a: unknown[]) => getProductMetrics(...a),
      getAiUsageReport: (...a: unknown[]) => getAiUsageReport(...a),
    },
    projections: {
      rebuildProjection: (...a: unknown[]) => rebuildProjection(...a),
      getProjectionRebuild: (...a: unknown[]) => getProjectionRebuild(...a),
    },
  },
}));

import { ProductMetricsPanel } from './product-metrics-panel';

const totals = (calls: number, cost: number, fallback = 0) => ({
  calls,
  tokensIn: calls * 100,
  tokensOut: calls * 20,
  costMicroUsd: cost,
  fallbackCalls: fallback,
  cacheHits: 1,
  fallbackShare: calls ? fallback / calls : null,
});

function renderPanel() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <ProductMetricsPanel />
    </QueryClientProvider>,
  );
}

describe('ProductMetricsPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    getProductMetrics.mockResolvedValue({
      from: new Date('2026-10-22'),
      to: new Date('2026-11-21'),
      funnel: { registered: 10, onboarded: 8, planCreated: 7, firstTaskDone: 5, activated: 4 },
      retention: [
        { name: 'D1', startDay: 1, endDay: 1, eligible: 10, retained: 6, rate: 0.6 },
        { name: 'D7', startDay: 7, endDay: 13, eligible: 4, retained: 2, rate: 0.5 },
        { name: 'D28', startDay: 28, endDay: 34, eligible: 0, retained: 0, rate: null },
      ],
      weekCompletionRate: 0.75,
      medianStudyDaysRatio: 0.62,
      learnersInSdr: 7,
      daily: [],
    });
    getAiUsageReport.mockResolvedValue({
      from: new Date('2026-10-22'),
      to: new Date('2026-11-21'),
      totals: totals(8, 4000, 1),
      byProvider: [{ key: 'GROQ', totals: totals(6, 3900, 1) }],
      byModel: [{ key: 'GROQ / openai/gpt-oss-120b', totals: totals(6, 3900, 1) }],
      byPrompt: [{ key: 'writing.grade.email', totals: totals(6, 3900, 1) }],
      byDay: [],
      activeLearners: 2,
      costPerActiveLearnerMicroUsd: 2000,
    });
  });

  it('docs/01 §7.1, §7.2 shows the North Star, week completion, the funnel and retention', async () => {
    renderPanel();

    expect(await screen.findByTestId('metric-sdr')).toHaveTextContent('62%');
    expect(screen.getByTestId('metric-sdr')).toHaveTextContent('7 learners');
    expect(screen.getByTestId('metric-week-completion')).toHaveTextContent('75%');
    const funnel = screen.getByTestId('funnel');
    expect(within(funnel).getByText('Activated in 48 h').parentElement).toHaveTextContent('440%');
    const retention = screen.getByTestId('retention');
    expect(within(retention).getByText('D7').closest('tr')).toHaveTextContent('50%2 of 4');
    expect(within(retention).getByText('D28').closest('tr')).toHaveTextContent('—');
  });

  it('docs/01 §7.2 shows AI spend per active learner and by provider, prompt and model', async () => {
    renderPanel();

    expect(await screen.findByTestId('metric-ai-per-learner')).toHaveTextContent('$0.0020');
    expect(await screen.findByTestId('ai-fallback')).toHaveTextContent('13%');
    expect(screen.getByText('writing.grade.email')).toBeInTheDocument();
    expect(screen.getByText('GROQ / openai/gpt-oss-120b')).toBeInTheDocument();
  });

  it('a period button reloads the metrics from a new start', async () => {
    renderPanel();
    await screen.findByTestId('metric-sdr');

    await userEvent.click(screen.getByRole('button', { name: 'Last 7 days' }));

    expect(screen.getByRole('button', { name: 'Last 7 days' })).toHaveAttribute('aria-pressed', 'true');
    const lastFrom = (getProductMetrics.mock.calls.at(-1)?.[0] as { from: Date }).from;
    const firstFrom = (getProductMetrics.mock.calls[0]?.[0] as { from: Date }).from;
    expect(lastFrom.getTime()).toBeGreaterThan(firstFrom.getTime());
  });

  it('ADR-0011 an admin rebuilds a read model and sees the number of replayed events', async () => {
    rebuildProjection.mockResolvedValue({ id: 'job-1', status: 'RUNNING' });
    getProjectionRebuild.mockResolvedValue({ id: 'job-1', status: 'COMPLETED', eventsReplayed: 42 });
    renderPanel();
    await screen.findByTestId('metric-sdr');

    const row = screen.getByText('AI usage').closest('li') as HTMLElement;
    await userEvent.click(within(row).getByRole('button', { name: 'Rebuild' }));

    expect(await within(row).findByRole('status')).toHaveTextContent('Rebuilt from 42 events');
    expect(rebuildProjection).toHaveBeenCalledWith(expect.objectContaining({ projection: 'ai-usage' }));
    expect((rebuildProjection.mock.calls[0]?.[0] as { idempotencyKey: string }).idempotencyKey).toMatch(/-/);
  });

  it('reports a load error', async () => {
    getProductMetrics.mockRejectedValue(new Error('down'));
    renderPanel();
    expect(await screen.findByRole('alert')).toHaveTextContent('could not be loaded');
  });
});
