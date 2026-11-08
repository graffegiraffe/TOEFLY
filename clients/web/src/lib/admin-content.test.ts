import { ItemStatus } from '@/api/generated/content';
import { actionsFor, cleanFilters, humanize, TARGET_STATUS } from './admin-content';

describe('admin content helpers', () => {
  it('US-28.01 an approved set can only be hidden, a hidden one can be restored or rejected', () => {
    expect(actionsFor(ItemStatus.Approved)).toEqual(['hide']);
    expect(actionsFor(ItemStatus.Hidden)).toEqual(['approve', 'reject']);
    expect(actionsFor(ItemStatus.InReview)).toEqual(['approve', 'reject']);
    expect(actionsFor(ItemStatus.Rejected)).toEqual([]);
  });

  it('maps every action to the status the API accepts', () => {
    expect(TARGET_STATUS).toEqual({ hide: 'HIDDEN', approve: 'APPROVED', reject: 'REJECTED' });
  });

  it('turns enum names into readable text', () => {
    expect(humanize('LISTEN_ACADEMIC_TALK')).toBe('Listen academic talk');
    expect(humanize('SEED_REMOVED')).toBe('Seed removed');
  });

  it('drops empty filters so cursors stay bound to real filters', () => {
    expect(cleanFilters({ status: undefined, taskType: '', legacy: false, reported: true, sourceCode: 'a' })).toEqual({
      reported: true,
      sourceCode: 'a',
    });
  });
});
