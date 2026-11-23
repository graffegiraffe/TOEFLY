import { Link } from '@tanstack/react-router';
import { ArrowRight, Check, Hourglass, Info, MessageCircle, Sparkles, UserRound } from 'lucide-react';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import type { WeeklyReview } from '@/api/generated/tutoragent';
import { Button, buttonClass } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { ProgressBar } from '@/components/ui/progress-bar';
import { cn } from '@/lib/cn';
import { bandDelta, bandRows, formatMinutes, scoreOf, sectionPercent, subcategoryTitle } from '@/lib/weekly-review';
import { SECTION_TAG } from '@/lib/weeks';

interface WeeklyReviewViewProps {
  review: WeeklyReview;
  /** Next week is built: link "See Week N+1", otherwise "Week N+1 is being prepared". */
  nextWeekReady: boolean;
  onAskTutor?: () => void;
}

/**
 * Weekly review page (docs/07-ux-ui.md §5.11, US-18.01): what was done, scores, the band estimate, top recurring
 * mistakes with examples and units, strengths, the focus of next week and a link to it. AI text is in the language
 * of the review; the interface stays English.
 */
export function WeeklyReviewView({ review, nextWeekReady, onAskTutor }: WeeklyReviewViewProps) {
  const { t } = useTranslation();
  const lang = review.language === 'RU' ? 'ru' : undefined;
  const next = review.weekNo + 1;
  const stats = review.stats;
  const snapshot = review.bandSnapshot;
  const rows = snapshot ? bandRows(snapshot) : [];
  const delta = bandDelta(snapshot?.overallDelta);

  return (
    <div className="flex max-w-[760px] flex-col gap-5" data-testid="weekly-review">
      {review.tutorNote && (
        <Section title={t('weeklyReview.fromTutor')} id="review-tutor">
          <p className="flex items-start gap-3 whitespace-pre-wrap text-body text-ink">
            <UserRound className="mt-0.5 size-5 flex-none text-ink-muted" aria-hidden="true" />
            {review.tutorNote}
          </p>
        </Section>
      )}

      <Card className="flex flex-col gap-2" lang={lang}>
        <p className="text-title-2 font-semibold text-ink" data-testid="review-headline">
          {review.headline}
        </p>
        <p className="text-body text-ink">{review.summary}</p>
        {review.aiFallback && (
          <p className="flex items-center gap-1.5 text-caption text-ink-muted" lang="en">
            <Info className="size-3.5" aria-hidden="true" />
            {t('weeklyReview.aiFallback')}
          </p>
        )}
      </Card>

      {review.lowActivity && (
        <div
          className="flex items-start gap-3 rounded-xl bg-info-soft px-4 py-3 text-body text-info"
          role="status"
          data-testid="low-activity"
        >
          <Info className="mt-0.5 size-4 flex-none" aria-hidden="true" />
          <div>
            <p className="font-semibold">{t('weeklyReview.lowActivity')}</p>
            <p>{t('weeklyReview.lowActivityText')}</p>
          </div>
        </div>
      )}

      <Section title={t('weeklyReview.whatYouDid')} id="review-did">
        <p className="text-title-3 font-semibold text-ink" data-testid="review-done">
          {t('weeklyReview.tasksDone', { done: stats.tasksDone, total: stats.tasksTotal })}
          {typeof stats.activeMinutes === 'number' && ` · ${formatMinutes(stats.activeMinutes)}`}
        </p>
        <ul className="mt-4 flex flex-col gap-3">
          {stats.sections.map((section) => (
            <li
              key={section.section}
              className="grid grid-cols-[96px_1fr] items-center gap-x-3 gap-y-1 sm:grid-cols-[110px_1fr_auto]"
            >
              <span
                className={cn(
                  'justify-self-start rounded-full px-2 py-0.5 text-[11px] font-semibold',
                  SECTION_TAG[section.section] ?? 'bg-surface-sunken text-ink-muted',
                )}
              >
                {t(`weeks.section.${section.section}`, { defaultValue: section.section })}
              </span>
              <ProgressBar
                value={sectionPercent(section)}
                label={t(`weeks.section.${section.section}`, { defaultValue: section.section })}
              />
              <span className="col-start-2 text-caption text-ink-muted sm:col-start-auto">
                {t('weeklyReview.sectionDone', { done: section.tasksDone, total: section.tasksTotal })} ·{' '}
                {t('weeklyReview.minutesVsPlan', { done: section.minutesDone, planned: section.minutesPlanned })}
              </span>
            </li>
          ))}
        </ul>
      </Section>

      <Section title={t('weeklyReview.scores')} id="review-scores">
        {review.scores.length === 0 ? (
          <p className="text-body text-ink-muted">{t('weeklyReview.noScores')}</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-body">
              <thead className="text-caption text-ink-muted">
                <tr>
                  <th scope="col" className="pb-2 font-semibold">
                    {t('weeklyReview.taskType')}
                  </th>
                  <th scope="col" className="pb-2 font-semibold">
                    {t('weeklyReview.attempts')}
                  </th>
                  <th scope="col" className="pb-2 font-semibold">
                    {t('weeklyReview.result')}
                  </th>
                </tr>
              </thead>
              <tbody>
                {review.scores.map((score) => {
                  const result = scoreOf(score);
                  return (
                    <tr key={score.taskType} className="border-t border-border">
                      <td className="py-2 pr-3 text-ink">
                        {t(`practice.taskType.${score.taskType}`, { defaultValue: score.taskType })}
                      </td>
                      <td className="py-2 pr-3 text-ink">{score.attempts}</td>
                      <td className="py-2 font-semibold text-ink">
                        {result
                          ? t(result.kind === 'accuracy' ? 'weeklyReview.accuracy' : 'weeklyReview.average', {
                              value: result.value,
                            })
                          : '—'}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
        {snapshot && (rows.length > 0 || typeof snapshot.overall === 'number') && (
          <div className="mt-5 flex flex-col gap-3 border-t border-border pt-4" data-testid="band-snapshot">
            <div className="flex flex-wrap items-center gap-2">
              <h3 className="text-body font-semibold text-ink">{t('weeklyReview.band')}</h3>
              <span className="inline-flex items-center gap-1 rounded-full bg-accent-soft px-2 py-0.5 text-micro font-semibold text-accent-on-soft">
                <Sparkles className="size-3" aria-hidden="true" />
                {t('weeklyReview.aiEstimate')}
              </span>
            </div>
            <dl className="grid grid-cols-2 gap-3 sm:grid-cols-5">
              {typeof snapshot.overall === 'number' && (
                <div className="rounded-lg bg-surface-sunken p-3">
                  <dt className="text-caption text-ink-muted">{t('weeklyReview.overall')}</dt>
                  <dd className="text-title-3 font-semibold text-ink">{snapshot.overall.toFixed(1)}</dd>
                  <dd className="text-caption text-ink-muted" data-testid="band-delta">
                    {delta ? t('weeklyReview.delta', { delta }) : t('weeklyReview.noChange')}
                  </dd>
                </div>
              )}
              {rows.map((row) => (
                <div key={row.section} className="rounded-lg bg-surface-sunken p-3">
                  <dt className="text-caption text-ink-muted">{t(`weeks.section.${row.section.toUpperCase()}`)}</dt>
                  <dd className="text-title-3 font-semibold text-ink">{row.value.toFixed(1)}</dd>
                </div>
              ))}
            </dl>
          </div>
        )}
      </Section>

      {!review.lowActivity && review.topMistakes.length > 0 && (
        <Section title={t('weeklyReview.mistakes')} id="review-mistakes">
          <ol className="flex flex-col gap-4" data-testid="top-mistakes">
            {review.topMistakes.map((mistake, i) => (
              <li key={`${mistake.subcategory}-${i}`} className="grid grid-cols-[28px_1fr] gap-3">
                <span className="grid size-7 place-items-center rounded-full bg-surface-sunken text-caption font-semibold text-ink">
                  {i + 1}
                </span>
                <div className="flex min-w-0 flex-col gap-1.5" lang={lang}>
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-semibold text-ink">
                      {mistake.pattern || subcategoryTitle(mistake.subcategory)}
                    </span>
                    <span
                      className="rounded-full bg-surface-sunken px-2 py-0.5 text-[11px] font-semibold text-ink-muted"
                      lang="en"
                    >
                      {subcategoryTitle(mistake.category)}
                    </span>
                    <span className="text-caption text-ink-muted" lang="en">
                      {t('weeklyReview.times', { count: mistake.count })}
                    </span>
                  </div>
                  {mistake.example && (
                    <p className="flex flex-wrap items-center gap-x-2 gap-y-1 text-body" lang="en">
                      <span className="sr-only">{t('weeklyReview.wrong')}: </span>
                      <del className="text-danger">{mistake.example.wrong}</del>
                      <ArrowRight className="size-3.5 text-ink-muted" aria-hidden="true" />
                      <span className="sr-only">{t('weeklyReview.right')}: </span>
                      <ins className="font-semibold text-success no-underline">{mistake.example.right}</ins>
                    </p>
                  )}
                  <p className="text-body text-ink">{mistake.advice}</p>
                  {mistake.unitRefs.length > 0 && (
                    <ul className="flex flex-wrap gap-1.5" lang="en">
                      {mistake.unitRefs.map((unit) => (
                        <li
                          key={unit.code}
                          className="rounded-md border border-border px-2 py-0.5 text-caption text-ink-muted"
                        >
                          {t('weeklyReview.review')}: {unit.code} — {unit.title}
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              </li>
            ))}
          </ol>
        </Section>
      )}

      {review.strengths.length > 0 && (
        <Section title={t('weeklyReview.strengths')} id="review-strengths">
          <ul className="flex flex-col gap-2" lang={lang}>
            {review.strengths.map((strength) => (
              <li key={strength} className="flex items-start gap-2 text-body text-ink">
                <Check className="mt-0.5 size-4 flex-none text-success" aria-hidden="true" />
                {strength}
              </li>
            ))}
          </ul>
        </Section>
      )}

      {review.nextWeekFocus.length > 0 && (
        <Section title={t('weeklyReview.focus', { number: next })} id="review-focus">
          <ol className="flex list-decimal flex-col gap-2 pl-5" lang={lang}>
            {review.nextWeekFocus.map((focus) => (
              <li key={focus.subcategory} className="text-body text-ink">
                <span className="font-semibold" lang="en">
                  {subcategoryTitle(focus.subcategory)}:
                </span>{' '}
                {focus.why}
              </li>
            ))}
          </ol>
        </Section>
      )}

      {review.motivation && (
        <Card className="flex items-start gap-3 bg-accent-soft" lang={lang}>
          <Sparkles className="mt-0.5 size-5 flex-none text-accent-on-soft" aria-hidden="true" />
          <p className="text-body font-medium text-ink" data-testid="review-motivation">
            {review.motivation}
          </p>
        </Card>
      )}

      <Card className="flex flex-wrap items-center gap-3">
        {nextWeekReady ? (
          <Link to="/weeks/$weekNumber" params={{ weekNumber: String(next) }} className={buttonClass('primary')}>
            {t('weeklyReview.seeNext', { number: next })}
            <ArrowRight className="size-4" aria-hidden="true" />
          </Link>
        ) : (
          <p className="flex flex-1 items-center gap-2 text-body text-ink">
            <Hourglass className="size-5 text-ink-muted" aria-hidden="true" />
            {t('weeklyReview.preparingNext', { number: next })}
          </p>
        )}
        {onAskTutor && (
          <Button variant="secondary" onClick={onAskTutor}>
            <MessageCircle className="size-4" aria-hidden="true" />
            {t('weeklyReview.askTutor')}
          </Button>
        )}
      </Card>
    </div>
  );
}

function Section({ title, id, children }: { title: string; id: string; children: ReactNode }) {
  return (
    <section aria-labelledby={id} className="rounded-xl border border-border bg-surface p-6 shadow-e1">
      <h2 id={id} className="mb-3 text-title-3 font-semibold text-ink">
        {title}
      </h2>
      {children}
    </section>
  );
}
