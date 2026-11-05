import {
  CalendarDays,
  ClipboardList,
  Dumbbell,
  FileText,
  Home,
  Languages,
  LineChart,
  ListChecks,
  Settings,
  SpellCheck,
  Sun,
  Users,
  type LucideIcon,
} from 'lucide-react';

export interface NavItem {
  to: string;
  labelKey: string;
  icon: LucideIcon;
}

// AC-05.03.1 (learner) and AC-05.03.3 (tutor). Sections without a screen yet open their empty state.
export const STUDENT_NAV: NavItem[] = [
  { to: '/', labelKey: 'nav.home', icon: Home },
  { to: '/today', labelKey: 'nav.today', icon: Sun },
  { to: '/weeks', labelKey: 'nav.weeks', icon: CalendarDays },
  { to: '/practice', labelKey: 'nav.practice', icon: Dumbbell },
  { to: '/grammar', labelKey: 'nav.grammar', icon: SpellCheck },
  { to: '/vocabulary', labelKey: 'nav.vocabulary', icon: Languages },
  { to: '/progress', labelKey: 'nav.progress', icon: LineChart },
];

export const TUTOR_NAV: NavItem[] = [
  { to: '/tutor/students', labelKey: 'nav.students', icon: Users },
  { to: '/tutor/plans', labelKey: 'nav.weekDrafts', icon: ClipboardList },
  { to: '/tutor/tasks', labelKey: 'nav.tasks', icon: ListChecks },
  { to: '/tutor/materials', labelKey: 'nav.materials', icon: FileText },
];

export const SETTINGS_NAV: NavItem = { to: '/settings', labelKey: 'nav.settings', icon: Settings };

export const PLACEHOLDER_SECTIONS = [
  'today',
  'weeks',
  'practice',
  'grammar',
  'vocabulary',
  'progress',
  'ai-tutor',
] as const;

export function navFor(roles: readonly string[]): { primary: NavItem[]; tutor: NavItem[] } {
  return {
    primary: roles.includes('STUDENT') ? STUDENT_NAV : [],
    tutor: roles.includes('TUTOR') ? TUTOR_NAV : [],
  };
}
