import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import '@/i18n';
import {
  AiCallOutcome,
  AiProvider,
  AiUseCase,
  GuardPolicy,
  PromptStatus,
  ProviderStatusCircuitEnum,
  QuotaDimension,
  ResponseError,
} from '@/api/generated/aigateway';

const listPrompts = vi.fn();
const listPromptVersions = vi.fn();
const activatePromptVersion = vi.fn();
const listAiCalls = vi.fn();
const listProviderStatuses = vi.fn();
const getAiUsage = vi.fn();
vi.mock('@/api/client', () => ({
  api: {
    aiAdmin: {
      listPrompts: (...a: unknown[]) => listPrompts(...a),
      listPromptVersions: (...a: unknown[]) => listPromptVersions(...a),
      activatePromptVersion: (...a: unknown[]) => activatePromptVersion(...a),
      listAiCalls: (...a: unknown[]) => listAiCalls(...a),
      listProviderStatuses: (...a: unknown[]) => listProviderStatuses(...a),
      getAiUsage: (...a: unknown[]) => getAiUsage(...a),
    },
  },
}));

import { AiCallsPanel } from './ai-calls-panel';
import { AiPromptsPanel } from './ai-prompts-panel';
import { AiProvidersPanel } from './ai-providers-panel';
import { AiUsagePanel } from './ai-usage-panel';

function renderWithClient(ui: React.ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>);
}

const prompt = {
  key: 'tutor.hint',
  useCase: AiUseCase.Hint,
  description: 'A hint without the answer',
  ownerService: 'tutor-agent-service',
  activeVersion: 2,
  canaryPercent: 0,
};

const version = (no: number, status: PromptStatus, over: Record<string, unknown> = {}) => ({
  key: 'tutor.hint',
  version: no,
  useCase: AiUseCase.Hint,
  description: `Version ${no} text`,
  system: 'system',
  user: 'user',
  params: {},
  maxInputTokens: 4000,
  untrusted: [],
  guardPolicy: GuardPolicy.Chat,
  status,
  active: status === PromptStatus.Active,
  createdBy: 'ada',
  createdAt: new Date('2026-11-01T10:00:00Z'),
  ...over,
});

const passedEval = (no: number) => ({
  id: `e${no}`,
  promptKey: 'tutor.hint',
  promptVersion: no,
  suite: 'hint-quality',
  mode: 'FULL',
  passed: true,
  metrics: {},
  ranAt: new Date('2026-11-02T10:00:00Z'),
  receivedAt: new Date('2026-11-02T10:00:00Z'),
});

const call = (over: Record<string, unknown> = {}) => ({
  id: 'c1',
  callerService: 'tutor-agent-service',
  useCase: AiUseCase.Hint,
  promptKey: 'tutor.hint',
  promptVersion: 2,
  provider: AiProvider.Groq,
  model: 'llama-3.3-70b-versatile',
  fallbackDepth: 1,
  degraded: true,
  cacheHit: false,
  inputTokens: 812,
  outputTokens: 96,
  latencyMs: 640,
  shadowCostMicroUsd: 2_350,
  outcome: AiCallOutcome.Success,
  requestMasked: 'My name is [PERSON_1]',
  responseMasked: 'Look at the second paragraph.',
  createdAt: new Date('2026-11-10T09:30:00Z'),
  ...over,
});

describe('AI administration', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    listPrompts.mockResolvedValue({ items: [prompt] });
    listPromptVersions.mockResolvedValue({
      items: [
        version(3, PromptStatus.Draft),
        version(2, PromptStatus.Active, { latestEval: passedEval(2) }),
        version(1, PromptStatus.Archived, { latestEval: passedEval(1) }),
      ],
    });
  });

  it('shows the versions of a prompt with their status, the active one and the latest eval run', async () => {
    renderWithClient(<AiPromptsPanel />);
    await userEvent.click(await screen.findByRole('button', { name: 'Versions: tutor.hint' }));

    const active = await screen.findByRole('listitem', { name: 'Version 2' });
    expect(within(active).getByText('Active now')).toBeInTheDocument();
    expect(within(active).getByText('Passed')).toBeInTheDocument();
    expect(within(active).getByText(/hint-quality · FULL/)).toBeInTheDocument();
    expect(within(active).queryByRole('button')).toBeNull();

    const draft = screen.getByRole('listitem', { name: 'Version 3' });
    expect(within(draft).getByText('Draft')).toBeInTheDocument();
    expect(within(draft).getByText('No eval run yet')).toBeInTheDocument();
    expect(within(draft).getByRole('button', { name: 'Activate' })).toBeInTheDocument();
  });

  it('rolls back by activating an archived version', async () => {
    activatePromptVersion.mockResolvedValue(version(1, PromptStatus.Active));
    renderWithClient(<AiPromptsPanel />);
    await userEvent.click(await screen.findByRole('button', { name: 'Versions: tutor.hint' }));
    const archived = await screen.findByRole('listitem', { name: 'Version 1' });
    await userEvent.click(within(archived).getByRole('button', { name: 'Roll back to this version' }));

    expect(window.confirm).toHaveBeenCalled();
    expect(activatePromptVersion).toHaveBeenCalledWith(
      expect.objectContaining({ promptKey: 'tutor.hint', version: 1, idempotencyKey: expect.any(String) }),
    );
    expect(await within(archived).findByText('Version 1 is active now.')).toBeInTheDocument();
  });

  it('explains a 409 EVALS_NOT_PASSED when a version without a passed eval run is activated', async () => {
    activatePromptVersion.mockRejectedValue(
      new ResponseError(
        new Response(JSON.stringify({ status: 409, code: 'EVALS_NOT_PASSED', title: 'Conflict' }), { status: 409 }),
      ),
    );
    renderWithClient(<AiPromptsPanel />);
    await userEvent.click(await screen.findByRole('button', { name: 'Versions: tutor.hint' }));
    const draft = await screen.findByRole('listitem', { name: 'Version 3' });
    await userEvent.click(within(draft).getByRole('button', { name: 'Activate' }));

    expect(await within(draft).findByRole('alert')).toHaveTextContent(
      'Version 3 cannot be activated: it has no passed eval run.',
    );
  });

  it('lists calls, filters them through the API and shows only the masked texts of one', async () => {
    listAiCalls.mockResolvedValue({ items: [call()], page: { hasMore: false, limit: 20 } });
    renderWithClient(<AiCallsPanel />);

    const row = (await screen.findByText('tutor.hint@2')).closest('tr')!;
    expect(within(row).getByText('Groq')).toBeInTheDocument();
    expect(within(row).getByText('812 in / 96 out')).toBeInTheDocument();
    expect(within(row).getByText('640 ms')).toBeInTheDocument();
    expect(within(row).getByText('$0.0024')).toBeInTheDocument();
    expect(within(row).getByText('Yes')).toBeInTheDocument();
    expect(within(row).getByText('Success')).toBeInTheDocument();

    await userEvent.selectOptions(screen.getByLabelText('Outcome'), 'PROVIDER_ERROR');
    expect(listAiCalls).toHaveBeenLastCalledWith(expect.objectContaining({ outcome: 'PROVIDER_ERROR', limit: 20 }));
  });

  it('opens the masked request and response of a call', async () => {
    listAiCalls.mockResolvedValue({ items: [call()], page: { hasMore: false, limit: 20 } });
    renderWithClient(<AiCallsPanel />);
    await userEvent.click(await screen.findByRole('button', { name: /Details of the call at/ }));
    expect(screen.getByText('My name is [PERSON_1]')).toBeInTheDocument();
    expect(screen.getByText('Look at the second paragraph.')).toBeInTheDocument();
  });

  it('loads the next page of calls with the cursor', async () => {
    listAiCalls
      .mockResolvedValueOnce({ items: [call()], page: { hasMore: true, nextCursor: 'next-1', limit: 20 } })
      .mockResolvedValueOnce({ items: [call({ id: 'c2', promptVersion: 1 })], page: { hasMore: false, limit: 20 } });
    renderWithClient(<AiCallsPanel />);
    await userEvent.click(await screen.findByRole('button', { name: 'Load more' }));
    expect(await screen.findByText('tutor.hint@1')).toBeInTheDocument();
    expect(listAiCalls).toHaveBeenLastCalledWith(expect.objectContaining({ cursor: 'next-1' }));
  });

  it('shows usage rows with totals and the cost in dollars', async () => {
    getAiUsage.mockResolvedValue({
      from: new Date('2026-11-09'),
      to: new Date('2026-11-15'),
      rows: [
        {
          day: new Date('2026-11-10'),
          provider: AiProvider.Groq,
          model: 'llama-3.3-70b-versatile',
          useCase: AiUseCase.Hint,
          calls: 120,
          cacheHits: 30,
          fallbacks: 4,
          inputTokens: 90_000,
          outputTokens: 10_000,
          audioSeconds: 0,
          characters: 0,
          shadowCostMicroUsd: 1_250_000,
        },
        {
          day: new Date('2026-11-11'),
          provider: AiProvider.AzureSpeech,
          model: 'pronunciation',
          useCase: AiUseCase.Pronunciation,
          calls: 10,
          cacheHits: 0,
          fallbacks: 0,
          inputTokens: 0,
          outputTokens: 0,
          audioSeconds: 300,
          characters: 0,
          shadowCostMicroUsd: 250_000,
        },
      ],
    });
    renderWithClient(<AiUsagePanel today={new Date(2026, 10, 15)} />);

    const table = await screen.findByRole('table', { name: 'Usage' });
    const total = within(table).getByRole('rowheader', { name: 'Total' }).closest('tr')!;
    expect(within(total).getByText('130')).toBeInTheDocument();
    expect(within(total).getByText('$1.50')).toBeInTheDocument();
    expect(screen.getByText(/Groq \$1\.25 · Azure speech \$0\.25/)).toBeInTheDocument();
    expect(getAiUsage).toHaveBeenCalledWith({ from: new Date(2026, 10, 9), to: new Date(2026, 10, 15) });
  });

  it('shows the quotas and circuit of each provider', async () => {
    listProviderStatuses.mockResolvedValue({
      items: [
        {
          provider: AiProvider.Groq,
          model: 'llama-3.3-70b-versatile',
          circuit: ProviderStatusCircuitEnum.HalfOpen,
          dimensions: [
            { dimension: QuotaDimension.Rpd, capacity: 1000, remaining: 50 },
            { dimension: QuotaDimension.Tpm, capacity: 6000, remaining: 6000 },
          ],
        },
      ],
    });
    renderWithClient(<AiProvidersPanel />);
    expect(await screen.findByText('Circuit half-open')).toBeInTheDocument();
    expect(screen.getByText('50 of 1,000 left')).toBeInTheDocument();
    expect(screen.getByText('Running low')).toBeInTheDocument();
    expect(screen.getByRole('progressbar', { name: 'Requests per day quota left' })).toHaveAttribute(
      'aria-valuenow',
      '5',
    );
  });
});
