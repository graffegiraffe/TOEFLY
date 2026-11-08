import { navFor } from './navigation';

describe('navigation', () => {
  it('AC-05.03.1 a learner sees Home, Today, Weeks, Practice, Grammar, Vocabulary and Progress', () => {
    const { primary, tutor } = navFor(['STUDENT']);

    expect(primary.map((item) => item.labelKey)).toEqual([
      'nav.home',
      'nav.today',
      'nav.weeks',
      'nav.practice',
      'nav.grammar',
      'nav.vocabulary',
      'nav.progress',
    ]);
    expect(tutor).toEqual([]);
  });

  it('AC-05.03.3 a tutor sees Students, Week drafts, Tasks and Materials', () => {
    const { primary, tutor } = navFor(['TUTOR']);

    expect(primary).toEqual([]);
    expect(tutor.map((item) => item.labelKey)).toEqual([
      'nav.students',
      'nav.weekDrafts',
      'nav.tasks',
      'nav.materials',
    ]);
  });

  it('only an administrator sees the content administration', () => {
    expect(navFor(['STUDENT']).admin).toEqual([]);
    expect(navFor(['ADMIN']).admin.map((item) => item.to)).toEqual(['/admin/content']);
  });
});
