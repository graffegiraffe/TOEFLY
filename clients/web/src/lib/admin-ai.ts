import {
  AiCallOutcome,
  PromptStatus,
  ProviderStatusCircuitEnum,
  ResponseError,
  type AiProvider,
  type PromptVersion,
  type QuotaDimensionStatus,
  type UsageRow,
} from '@/api/generated/aigateway';
import { addDays, startOfDay } from './dates';

/** Badge tones mapped to the design tokens; the text always names the state, colour only supports it. */
export type Tone = 'neutral' | 'accent' | 'success' | 'warning' | 'danger';

export const PROMPT_STATUS_TONE: Record<PromptStatus, Tone> = {
  [PromptStatus.Draft]: 'neutral',
  [PromptStatus.Candidate]: 'warning',
  [PromptStatus.Active]: 'success',
  [PromptStatus.Archived]: 'neutral',
};

export const CIRCUIT_TONE: Record<ProviderStatusCircuitEnum, Tone> = {
  [ProviderStatusCircuitEnum.Closed]: 'success',
  [ProviderStatusCircuitEnum.HalfOpen]: 'warning',
  [ProviderStatusCircuitEnum.Open]: 'danger',
  [ProviderStatusCircuitEnum.ForcedOpen]: 'danger',
  [ProviderStatusCircuitEnum.Disabled]: 'neutral',
  [ProviderStatusCircuitEnum.MetricsOnly]: 'neutral',
};

export const OUTCOME_TONE: Record<AiCallOutcome, Tone> = {
  [AiCallOutcome.Success]: 'success',
  [AiCallOutcome.CacheHit]: 'accent',
  [AiCallOutcome.QuotaExhausted]: 'warning',
  [AiCallOutcome.ProviderError]: 'danger',
  [AiCallOutcome.OutputInvalid]: 'danger',
  [AiCallOutcome.GuardBlocked]: 'warning',
};

/** What an administrator can do with a version: activate a new one or roll back to an archived one. */
export type VersionAction = 'activate' | 'rollback';

export function versionAction(version: Pick<PromptVersion, 'status' | 'active'>): VersionAction | null {
  if (version.active || version.status === PromptStatus.Active) {
    return null;
  }
  return version.status === PromptStatus.Archived ? 'rollback' : 'activate';
}

/** The active version of a prompt, if any; the list comes newest first. */
export function activeVersionOf(versions: readonly PromptVersion[]): PromptVersion | undefined {
  return versions.find((version) => version.active || version.status === PromptStatus.Active);
}

export const EVALS_NOT_PASSED = 'EVALS_NOT_PASSED';

/** Error of an admin command with the `code` of the Problem Details body (docs/05-api-contracts.md §2). */
export class AiAdminError extends Error {
  constructor(
    readonly status: number,
    readonly code: string | undefined,
  ) {
    super(code ?? `HTTP ${status}`);
    this.name = 'AiAdminError';
  }
}

/** Reads the Problem Details `code` of a failed call; other failures pass through unchanged. */
export async function toAiAdminError(error: unknown): Promise<unknown> {
  if (!(error instanceof ResponseError)) {
    return error;
  }
  let code: string | undefined;
  try {
    const body: unknown = await error.response.clone().json();
    if (body && typeof body === 'object' && typeof (body as { code?: unknown }).code === 'string') {
      code = (body as { code: string }).code;
    }
  } catch {
    code = undefined;
  }
  return new AiAdminError(error.response.status, code);
}

export function isEvalsNotPassed(error: unknown): boolean {
  return error instanceof AiAdminError && error.code === EVALS_NOT_PASSED;
}

/** The ledger keeps cost in micro-dollars; the screens show dollars. */
export function microToUsd(micro: number): number {
  return micro / 1_000_000;
}

export function formatUsd(micro: number): string {
  const usd = microToUsd(micro);
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    minimumFractionDigits: 2,
    maximumFractionDigits: usd !== 0 && Math.abs(usd) < 1 ? 4 : 2,
  }).format(usd);
}

export function formatNumber(value: number): string {
  return new Intl.NumberFormat('en-US', { maximumFractionDigits: 1 }).format(value);
}

export interface UsageTotals {
  calls: number;
  cacheHits: number;
  fallbacks: number;
  inputTokens: number;
  outputTokens: number;
  audioSeconds: number;
  characters: number;
  shadowCostMicroUsd: number;
}

export function usageTotals(rows: readonly UsageRow[]): UsageTotals {
  const totals: UsageTotals = {
    calls: 0,
    cacheHits: 0,
    fallbacks: 0,
    inputTokens: 0,
    outputTokens: 0,
    audioSeconds: 0,
    characters: 0,
    shadowCostMicroUsd: 0,
  };
  for (const row of rows) {
    totals.calls += row.calls;
    totals.cacheHits += row.cacheHits;
    totals.fallbacks += row.fallbacks;
    totals.inputTokens += row.inputTokens;
    totals.outputTokens += row.outputTokens;
    totals.audioSeconds += row.audioSeconds;
    totals.characters += row.characters;
    totals.shadowCostMicroUsd += row.shadowCostMicroUsd;
  }
  return totals;
}

/** Cost per provider, largest first. */
export function costByProvider(rows: readonly UsageRow[]): { provider: AiProvider; shadowCostMicroUsd: number }[] {
  const sums = new Map<AiProvider, number>();
  for (const row of rows) {
    sums.set(row.provider, (sums.get(row.provider) ?? 0) + row.shadowCostMicroUsd);
  }
  return [...sums.entries()]
    .map(([provider, shadowCostMicroUsd]) => ({ provider, shadowCostMicroUsd }))
    .sort((a, b) => b.shadowCostMicroUsd - a.shadowCostMicroUsd);
}

export type QuotaLevel = 'ok' | 'low' | 'empty';

/** Share of a quota still available, 0–100. */
export function quotaPercent(dimension: Pick<QuotaDimensionStatus, 'capacity' | 'remaining'>): number {
  if (dimension.capacity <= 0) {
    return 0;
  }
  return Math.max(0, Math.min(100, (dimension.remaining / dimension.capacity) * 100));
}

export function quotaLevel(dimension: Pick<QuotaDimensionStatus, 'capacity' | 'remaining'>): QuotaLevel {
  const percent = quotaPercent(dimension);
  if (percent <= 0) {
    return 'empty';
  }
  return percent < 20 ? 'low' : 'ok';
}

/** `yyyy-mm-dd` of a local date, the value of `<input type="date">`. */
export function toDateInput(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/** Local midnight of a `yyyy-mm-dd` value, or undefined for an empty or broken one. */
export function fromDateInput(value: string | undefined): Date | undefined {
  const match = value?.match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if (!match) {
    return undefined;
  }
  return new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]));
}

/** The last seven days up to today, both ends included. */
export function defaultUsageRange(today: Date): { from: string; to: string } {
  return { from: toDateInput(addDays(today, -6)), to: toDateInput(startOfDay(today)) };
}

export interface CallFilters {
  promptKey?: string;
  provider?: AiProvider;
  outcome?: AiCallOutcome;
  /** `yyyy-mm-dd`, the start of that local day. */
  from?: string;
  /** `yyyy-mm-dd`, included up to the end of that local day. */
  to?: string;
}

/** Empty filters are dropped; days become the instants the API filters by. */
export function callQueryParams(filters: CallFilters): {
  promptKey?: string;
  provider?: AiProvider;
  outcome?: AiCallOutcome;
  from?: Date;
  to?: Date;
} {
  const from = fromDateInput(filters.from);
  const toDay = fromDateInput(filters.to);
  return Object.fromEntries(
    Object.entries({
      promptKey: filters.promptKey || undefined,
      provider: filters.provider || undefined,
      outcome: filters.outcome || undefined,
      from,
      to: toDay ? addDays(toDay, 1) : undefined,
    }).filter(([, value]) => value !== undefined),
  );
}

/** `prompt@version`, or a dash for calls made without a registry prompt (speech, embeddings). */
export function promptLabel(call: { promptKey?: string | null; promptVersion?: number | null }): string {
  if (!call.promptKey) {
    return '—';
  }
  return call.promptVersion ? `${call.promptKey}@${call.promptVersion}` : call.promptKey;
}

/** A `format: date` value is parsed as UTC midnight; this is the same calendar day as a local date. */
export function localDay(day: Date): Date {
  return new Date(day.getUTCFullYear(), day.getUTCMonth(), day.getUTCDate());
}
