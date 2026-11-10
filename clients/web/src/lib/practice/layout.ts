/** How a player lays out a task type (docs/07-ux-ui.md §5.9). */
export type Layout = 'cloze' | 'list' | 'step';

export function layoutOf(taskType: string): Layout {
  if (taskType === 'COMPLETE_THE_WORDS') return 'cloze';
  if (taskType === 'GRAMMAR_EXERCISE' || taskType === 'VOCAB_EXERCISE') return 'list';
  return 'step';
}
