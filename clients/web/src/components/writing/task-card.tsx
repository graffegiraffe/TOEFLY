import { useTranslation } from 'react-i18next';
import { cn } from '@/lib/cn';
import { initialsOf, type Post, type WritingStimulus } from './content';

/** The task of a Writing prompt (docs/07-ux-ui.md §5.9 items 9 and 10): email situation or the discussion board. */
export function TaskCard({ taskType, stimulus }: { taskType: string; stimulus: WritingStimulus }) {
  return taskType === 'WRITE_EMAIL' ? <EmailTask stimulus={stimulus} /> : <DiscussionTask stimulus={stimulus} />;
}

function EmailTask({ stimulus }: { stimulus: WritingStimulus }) {
  const { t } = useTranslation();
  return (
    <div className="flex flex-col gap-4 text-body text-ink" data-testid="task-card">
      {stimulus.scenario && <p className="whitespace-pre-line">{stimulus.scenario}</p>}
      {stimulus.bullets && stimulus.bullets.length > 0 && (
        <div className="flex flex-col gap-2">
          <p className="font-semibold">{t('writing.email.doTheFollowing')}</p>
          <ul className="flex list-disc flex-col gap-1 pl-5">
            {stimulus.bullets.map((bullet) => (
              <li key={bullet.id}>{bullet.text}</li>
            ))}
          </ul>
        </div>
      )}
      <p className="text-ink-muted">{t('writing.email.completeSentences')}</p>
    </div>
  );
}

function DiscussionTask({ stimulus }: { stimulus: WritingStimulus }) {
  const { t } = useTranslation();
  return (
    <div className="flex flex-col gap-4 text-body text-ink" data-testid="task-card">
      <p className="text-ink-muted">
        {stimulus.course
          ? t('writing.discussion.introCourse', { course: stimulus.course })
          : t('writing.discussion.intro')}
      </p>
      {stimulus.professor && <PostCard post={stimulus.professor} professor label={t('writing.discussion.professor')} />}
      {(stimulus.students ?? []).map((student, index) => (
        <PostCard key={`${student.name}-${index}`} post={student} label={t('writing.discussion.student')} />
      ))}
    </div>
  );
}

function PostCard({ post, professor = false, label }: { post: Post; professor?: boolean; label: string }) {
  return (
    <figure
      className={cn('flex gap-3 rounded-xl border border-border p-4', professor ? 'bg-surface' : 'bg-surface-sunken')}
    >
      <span
        aria-hidden="true"
        className={cn(
          'grid size-10 flex-none place-items-center rounded-full text-caption font-semibold',
          professor ? 'bg-primary text-white' : 'bg-accent-soft text-accent-on-soft',
        )}
      >
        {initialsOf(post.name)}
      </span>
      <div className="min-w-0 flex-1">
        <figcaption className="text-caption">
          <span className="font-semibold text-ink">{post.name}</span>
          <span className="text-ink-muted"> · {label}</span>
        </figcaption>
        <p className="mt-1 whitespace-pre-line">{post.post}</p>
      </div>
    </figure>
  );
}
