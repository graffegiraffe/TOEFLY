import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import '@/i18n';
import { ItemStatus, ReportReason, ReportStatus, TaskType, ExamFormat, Provenance } from '@/api/generated/content';

const listItemSets = vi.fn();
const patch = vi.fn();
const listReports = vi.fn();
const resolve = vi.fn();
const listGrammarUnits = vi.fn();
const listVocabularyUnits = vi.fn();
const approveGrammarUnit = vi.fn();
vi.mock('@/api/client', () => ({
  api: {
    contentAdminUnits: {
      adminListGrammarUnits: (...a: unknown[]) => listGrammarUnits(...a),
      adminListVocabularyUnits: (...a: unknown[]) => listVocabularyUnits(...a),
      adminApproveGrammarUnit: (...a: unknown[]) => approveGrammarUnit(...a),
      adminApproveVocabularyUnit: vi.fn(),
    },
    contentAdmin: {
      adminListItemSets: (...a: unknown[]) => listItemSets(...a),
      adminPatchItemSet: (...a: unknown[]) => patch(...a),
      adminListItemReports: (...a: unknown[]) => listReports(...a),
      adminResolveItemReport: (...a: unknown[]) => resolve(...a),
      adminGetItemSet: vi.fn(),
    },
    contentSources: { listSources: () => Promise.resolve({ items: [] }) },
  },
}));

import { ItemSetsPanel } from './item-sets-panel';
import { ReportsPanel } from './reports-panel';
import { StatusBadge } from './status-badge';
import { UnitsPanel } from './units-panel';

function renderWithClient(ui: React.ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>);
}

const row = (over: Record<string, unknown> = {}) => ({
  id: 'a1',
  version: 1,
  taskType: TaskType.ReadDailyLife,
  examFormat: ExamFormat.Toefl2026,
  cefr: 'B1',
  topic: 'Campus library',
  status: ItemStatus.Approved,
  provenance: Provenance.Extracted,
  sourceCode: 'demo',
  itemCount: 2,
  openReports: 1,
  ...over,
});

describe('Content administration', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    listItemSets.mockResolvedValue({ items: [row()], page: { hasMore: false } });
    patch.mockResolvedValue(row({ status: ItemStatus.Hidden }));
  });

  it('shows the status as text, not only as colour', () => {
    render(<StatusBadge status={ItemStatus.InReview} />);
    expect(screen.getByText('In review')).toBeInTheDocument();
  });

  it('US-28.01 hiding a set sends HIDDEN for that set', async () => {
    renderWithClient(<ItemSetsPanel />);
    await userEvent.click(await screen.findByRole('button', { name: 'Hide' }));
    expect(patch).toHaveBeenCalledWith({ itemSetId: 'a1', adminItemSetPatch: { status: 'HIDDEN' } });
  });

  it('a hidden set offers Restore and Reject, a rejected one offers nothing', async () => {
    listItemSets.mockResolvedValue({
      items: [
        row({ id: 'h', status: ItemStatus.Hidden, topic: 'Hidden one' }),
        row({ id: 'r', status: ItemStatus.Rejected, topic: 'Rejected one' }),
      ],
      page: { hasMore: false },
    });
    renderWithClient(<ItemSetsPanel />);
    expect(await screen.findByRole('button', { name: 'Restore' })).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: 'Reject' })).toHaveLength(1);
  });

  it('filters by status through the API', async () => {
    renderWithClient(<ItemSetsPanel />);
    await screen.findByText('Campus library');
    await userEvent.selectOptions(screen.getByLabelText('Status'), 'HIDDEN');
    expect(listItemSets).toHaveBeenLastCalledWith(expect.objectContaining({ status: 'HIDDEN' }));
  });

  it('shows the empty state when nothing matches', async () => {
    listItemSets.mockResolvedValue({ items: [], page: { hasMore: false } });
    renderWithClient(<ItemSetsPanel />);
    expect(await screen.findByText('No item sets match these filters.')).toBeInTheDocument();
  });

  it('a report can be resolved with the set hidden', async () => {
    listReports.mockResolvedValue({
      items: [
        {
          id: 'r1',
          itemSetId: 'a1',
          reason: ReportReason.WrongKey,
          comment: 'Key B looks wrong',
          status: ReportStatus.Open,
          createdAt: new Date('2026-11-06T10:00:00Z'),
        },
      ],
    });
    resolve.mockResolvedValue({});
    renderWithClient(<ReportsPanel />);
    expect(await screen.findByText('Key B looks wrong')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Resolve and hide the set' }));
    expect(resolve).toHaveBeenCalledWith({
      reportId: 'r1',
      resolveItemReportRequest: { outcome: 'RESOLVED', hideItemSet: true },
    });
  });

  it('lists units with their extraction warnings and approves one in review', async () => {
    listGrammarUnits.mockResolvedValue({
      items: [
        {
          id: 'g1',
          framework: 'COLLINS_GRAMMAR',
          unitNo: 6,
          title: 'Conditional sentences',
          status: ItemStatus.InReview,
          sourceCode: 'collins',
          topicCodes: ['conditional-sentences'],
          exercises: [{ role: 'EXERCISE' }],
          extraction: {
            method: 'OCR_LLM',
            reviewed: false,
            warnings: ['grammar-u06: exercise B: 1 of 8 items skipped'],
          },
        },
      ],
    });
    listVocabularyUnits.mockResolvedValue({ items: [] });
    approveGrammarUnit.mockResolvedValue({});
    renderWithClient(<UnitsPanel />);

    expect(await screen.findByText('Unit 6 · Conditional sentences')).toBeInTheDocument();
    expect(screen.getByText('1 waits for review')).toBeInTheDocument();
    await userEvent.click(screen.getByText('1 extraction warning'));
    expect(screen.getByText('grammar-u06: exercise B: 1 of 8 items skipped')).toBeVisible();
    expect(screen.getByText(/Compare the content and the answer keys/)).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Approve' }));
    expect(approveGrammarUnit).toHaveBeenCalledWith({ unitId: 'g1' });
  });
});
