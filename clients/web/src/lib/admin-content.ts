import { AdminItemSetPatchStatusEnum, ItemStatus } from '@/api/generated/content';

/** Status changes an administrator may request; they follow the state machine of docs/03-domain-model.md §3.7. */
export type StatusAction = 'hide' | 'approve' | 'reject';

const ACTIONS: Record<ItemStatus, StatusAction[]> = {
  [ItemStatus.Draft]: ['approve', 'reject'],
  [ItemStatus.InReview]: ['approve', 'reject'],
  [ItemStatus.Approved]: ['hide'],
  [ItemStatus.Hidden]: ['approve', 'reject'],
  [ItemStatus.Rejected]: [],
};

export function actionsFor(status: ItemStatus): StatusAction[] {
  return ACTIONS[status];
}

export const TARGET_STATUS: Record<StatusAction, AdminItemSetPatchStatusEnum> = {
  hide: AdminItemSetPatchStatusEnum.Hidden,
  approve: AdminItemSetPatchStatusEnum.Approved,
  reject: AdminItemSetPatchStatusEnum.Rejected,
};

/** `LISTEN_ACADEMIC_TALK` to `Listen academic talk`. */
export function humanize(value: string): string {
  const words = value.toLowerCase().split('_').join(' ');
  return words.charAt(0).toUpperCase() + words.slice(1);
}

export interface ContentFilters {
  status?: ItemStatus;
  taskType?: string;
  sourceCode?: string;
  legacy?: boolean;
  reported?: boolean;
}

/** Empty values are dropped so that the query key and the request carry only real filters. */
export function cleanFilters(filters: ContentFilters): ContentFilters {
  return Object.fromEntries(
    Object.entries(filters).filter(([, value]) => value !== undefined && value !== '' && value !== false),
  ) as ContentFilters;
}
