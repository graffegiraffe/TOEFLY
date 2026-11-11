const WEEK_COVERS = [
  'week-01-campus',
  'week-02-library',
  'week-03-lecture-hall',
  'week-04-exam-desk',
  'week-05-headphones',
  'week-06-microphone',
  'week-07-open-books',
  'week-08-letter',
  'week-09-globe',
  'week-10-laboratory',
  'week-11-quad',
  'week-12-dormitory',
  'week-13-museum',
  'week-14-observatory',
  'week-15-cafeteria',
  'week-16-graduation',
];

/** Cover of a week: the exam desk for mock exam weeks, a rotation of the other 15 (docs/07-ux-ui.md §8). */
export function weekCoverId(weekNo: number, mockExam: boolean): string {
  if (mockExam) {
    return 'week-04-exam-desk';
  }
  let index = (weekNo - 1) % WEEK_COVERS.length;
  if (index === 3) {
    index = 4;
  }
  return WEEK_COVERS[index] ?? 'week-01-campus';
}

export function sectionCoverId(section: string): string {
  return `section-${section.toLowerCase()}`;
}
