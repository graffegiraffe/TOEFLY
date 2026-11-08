import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { adminGrammarUnitsQuery, adminVocabularyUnitsQuery, useApproveUnit, type UnitKind } from '@/api/content';
import { ItemStatus, type Extraction } from '@/api/generated/content';
import { Button } from '@/components/ui/button';
import { EmptyState } from '@/components/ui/empty-state';
import { ExtractionNotes } from './extraction-notes';
import { StatusBadge } from './status-badge';

interface UnitRow {
  id: string;
  unitNo: number;
  title: string;
  status: ItemStatus;
  sourceCode: string;
  exercises: object[];
  words?: object[];
  extraction?: Extraction;
}

/** Grammar and vocabulary units: units read from noisy scans wait in review until an administrator approves them. */
export function UnitsPanel() {
  const { t } = useTranslation();
  const grammar = useQuery(adminGrammarUnitsQuery);
  const vocabulary = useQuery(adminVocabularyUnitsQuery);
  return (
    <div className="flex flex-col gap-8">
      <UnitTable kind="grammar" title={t('admin.units.grammar')} rows={grammar.data?.items} failed={grammar.isError} />
      <UnitTable
        kind="vocabulary"
        title={t('admin.units.vocabulary')}
        rows={vocabulary.data?.items}
        failed={vocabulary.isError}
      />
    </div>
  );
}

function UnitTable({
  kind,
  title,
  rows,
  failed,
}: {
  kind: UnitKind;
  title: string;
  rows: UnitRow[] | undefined;
  failed: boolean;
}) {
  const { t } = useTranslation();
  const waiting = rows?.filter((row) => row.status !== ItemStatus.Approved).length ?? 0;
  return (
    <section aria-labelledby={`units-${kind}`} className="flex flex-col gap-3">
      <h2 id={`units-${kind}`} className="text-title-3 font-semibold">
        {title}
        {waiting > 0 && (
          <span className="ml-2 text-caption font-normal text-ink-muted">
            {t('admin.units.waiting', { count: waiting })}
          </span>
        )}
      </h2>
      {failed && (
        <p role="alert" className="text-danger">
          {t('admin.error')}
        </p>
      )}
      {rows && rows.length === 0 && <EmptyState title={t('admin.units.empty')} />}
      {rows && rows.length > 0 && (
        <div className="overflow-x-auto rounded-xl border border-border">
          <table className="w-full min-w-[640px] border-collapse text-left text-caption">
            <caption className="sr-only">{title}</caption>
            <thead className="bg-surface-sunken text-ink-muted">
              <tr>
                {(['unit', 'source', 'content', 'status', 'actions'] as const).map((column) => (
                  <th key={column} scope="col" className="px-3 py-2 font-semibold">
                    {t(`admin.units.columns.${column}`)}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <Row key={row.id} kind={kind} row={row} />
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

function Row({ kind, row }: { kind: UnitKind; row: UnitRow }) {
  const { t } = useTranslation();
  const approve = useApproveUnit();
  const warnings = row.extraction?.warnings.length ?? 0;
  return (
    <tr className="border-t border-border align-top">
      <td className="px-3 py-2">
        <div className="font-semibold text-ink">
          {t('admin.units.unit', { no: row.unitNo })} · {row.title}
        </div>
        {row.extraction && (
          <details className="mt-1">
            <summary className="cursor-pointer text-ink-muted">
              {t('admin.units.warnings', { count: warnings })}
            </summary>
            <div className="mt-2">
              <ExtractionNotes extraction={row.extraction} />
            </div>
          </details>
        )}
      </td>
      <td className="px-3 py-2">{row.sourceCode}</td>
      <td className="px-3 py-2">
        {row.words !== undefined && <div>{t('admin.units.words', { count: row.words.length })}</div>}
        <div>{t('admin.units.exercises', { count: row.exercises.length })}</div>
      </td>
      <td className="px-3 py-2">
        <StatusBadge status={row.status} />
      </td>
      <td className="px-3 py-2">
        {row.status !== ItemStatus.Approved && (
          <Button
            variant="secondary"
            className="h-8 px-3 text-caption"
            disabled={approve.isPending}
            onClick={() => approve.mutate({ kind, unitId: row.id })}
          >
            {t('admin.action.approve')}
          </Button>
        )}
        {approve.isError && (
          <p role="alert" className="mt-1 text-danger">
            {t('admin.error')}
          </p>
        )}
      </td>
    </tr>
  );
}
