import { AiCallOutcome, AiProvider, AiUseCase, PromptStatus, ResponseError } from '@/api/generated/aigateway';
import {
  activeVersionOf,
  AiAdminError,
  callQueryParams,
  costByProvider,
  defaultUsageRange,
  formatUsd,
  fromDateInput,
  isEvalsNotPassed,
  localDay,
  microToUsd,
  promptLabel,
  quotaLevel,
  quotaPercent,
  toAiAdminError,
  toDateInput,
  usageTotals,
  versionAction,
} from './admin-ai';

const usageRow = (over: Record<string, unknown> = {}) => ({
  day: new Date('2026-11-10'),
  provider: AiProvider.Groq,
  model: 'llama-3.3-70b-versatile',
  useCase: AiUseCase.Hint,
  calls: 10,
  cacheHits: 2,
  fallbacks: 1,
  inputTokens: 1000,
  outputTokens: 200,
  audioSeconds: 0,
  characters: 0,
  shadowCostMicroUsd: 1_500,
  ...over,
});

describe('AI administration helpers', () => {
  it('offers activation for a draft or candidate, rollback for an archived version and nothing for the active one', () => {
    expect(versionAction({ status: PromptStatus.Draft })).toBe('activate');
    expect(versionAction({ status: PromptStatus.Candidate })).toBe('activate');
    expect(versionAction({ status: PromptStatus.Archived })).toBe('rollback');
    expect(versionAction({ status: PromptStatus.Active, active: true })).toBeNull();
  });

  it('finds the active version in a list that comes newest first', () => {
    const versions = [
      { version: 3, status: PromptStatus.Candidate },
      { version: 2, status: PromptStatus.Active, active: true },
      { version: 1, status: PromptStatus.Archived },
    ];
    expect(activeVersionOf(versions as never)?.version).toBe(2);
  });

  it('reads the Problem Details code of a 409 so that EVALS_NOT_PASSED gets its own message', async () => {
    const response = new Response(JSON.stringify({ status: 409, code: 'EVALS_NOT_PASSED', title: 'Conflict' }), {
      status: 409,
      headers: { 'content-type': 'application/problem+json' },
    });
    const error = await toAiAdminError(new ResponseError(response));
    expect(error).toEqual(new AiAdminError(409, 'EVALS_NOT_PASSED'));
    expect(isEvalsNotPassed(error)).toBe(true);
    expect(isEvalsNotPassed(new AiAdminError(409, 'CONFLICT'))).toBe(false);
  });

  it('keeps a failure without a body as a plain status and passes network errors through', async () => {
    const error = await toAiAdminError(new ResponseError(new Response(null, { status: 500 })));
    expect(error).toEqual(new AiAdminError(500, undefined));
    const offline = new TypeError('Failed to fetch');
    expect(await toAiAdminError(offline)).toBe(offline);
  });

  it('turns micro-dollars into dollars, with four decimals for amounts under a dollar', () => {
    expect(microToUsd(2_500_000)).toBe(2.5);
    expect(formatUsd(2_500_000)).toBe('$2.50');
    expect(formatUsd(1_500)).toBe('$0.0015');
    expect(formatUsd(0)).toBe('$0.00');
  });

  it('sums the usage rows and ranks providers by cost', () => {
    const rows = [
      usageRow(),
      usageRow({ provider: AiProvider.AzureSpeech, calls: 3, shadowCostMicroUsd: 40_000, audioSeconds: 90.5 }),
      usageRow({ shadowCostMicroUsd: 500 }),
    ];
    expect(usageTotals(rows)).toEqual({
      calls: 23,
      cacheHits: 6,
      fallbacks: 3,
      inputTokens: 3000,
      outputTokens: 600,
      audioSeconds: 90.5,
      characters: 0,
      shadowCostMicroUsd: 42_000,
    });
    expect(costByProvider(rows)).toEqual([
      { provider: AiProvider.AzureSpeech, shadowCostMicroUsd: 40_000 },
      { provider: AiProvider.Groq, shadowCostMicroUsd: 2_000 },
    ]);
  });

  it('measures what is left of a quota', () => {
    expect(quotaPercent({ capacity: 1000, remaining: 250 })).toBe(25);
    expect(quotaLevel({ capacity: 1000, remaining: 250 })).toBe('ok');
    expect(quotaLevel({ capacity: 1000, remaining: 100 })).toBe('low');
    expect(quotaLevel({ capacity: 1000, remaining: 0 })).toBe('empty');
    expect(quotaLevel({ capacity: 0, remaining: 0 })).toBe('empty');
    expect(quotaPercent({ capacity: 10, remaining: 12 })).toBe(100);
  });

  it('works with local days for date inputs and the usage range', () => {
    expect(toDateInput(new Date(2026, 10, 5))).toBe('2026-11-05');
    expect(fromDateInput('2026-11-05')).toEqual(new Date(2026, 10, 5));
    expect(fromDateInput('')).toBeUndefined();
    expect(defaultUsageRange(new Date(2026, 10, 15, 18, 30))).toEqual({ from: '2026-11-09', to: '2026-11-15' });
    expect(toDateInput(localDay(new Date('2026-11-10')))).toBe('2026-11-10');
  });

  it('drops empty call filters and includes the whole last day', () => {
    expect(callQueryParams({ promptKey: '', provider: AiProvider.Groq, from: '2026-11-01', to: '2026-11-02' })).toEqual(
      {
        provider: AiProvider.Groq,
        from: new Date(2026, 10, 1),
        to: new Date(2026, 10, 3),
      },
    );
    expect(callQueryParams({ outcome: AiCallOutcome.ProviderError })).toEqual({ outcome: 'PROVIDER_ERROR' });
  });

  it('labels a call with prompt@version or a dash without a prompt', () => {
    expect(promptLabel({ promptKey: 'tutor.hint', promptVersion: 4 })).toBe('tutor.hint@4');
    expect(promptLabel({ promptKey: null })).toBe('—');
  });
});
