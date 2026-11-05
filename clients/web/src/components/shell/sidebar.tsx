import { Link } from '@tanstack/react-router';
import { LogOut } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { GlassPanel } from '@/components/ui/card';
import { Logo } from '@/components/ui/logo';
import { navFor, SETTINGS_NAV, type NavItem } from './navigation';

interface SidebarProps {
  roles: readonly string[];
  displayName: string;
  onSignOut: () => void;
}

function NavLink({ item }: { item: NavItem }) {
  const { t } = useTranslation();
  const Icon = item.icon;
  return (
    <Link
      to={item.to}
      activeOptions={{ exact: item.to === '/' }}
      className="flex items-center gap-2.5 rounded-[10px] px-2.5 py-2 text-[14px] font-medium leading-5 text-neutral-700 hover:bg-[var(--glass-item-active)] dark:text-[#CFE0D5]"
      activeProps={{
        className: 'glass-item-active !font-semibold !text-brand-700 dark:!text-brand-500',
        'aria-current': 'page',
      }}
    >
      <Icon className="size-4 flex-none" aria-hidden="true" />
      {t(item.labelKey)}
    </Link>
  );
}

/** Glass sidebar, 220px, 14px from the window edges (docs/07-ux-ui.md §4.2). */
export function Sidebar({ roles, displayName, onSignOut }: SidebarProps) {
  const { t } = useTranslation();
  const nav = navFor(roles);
  const initials = displayName
    .split(/\s+/)
    .map((part) => part[0] ?? '')
    .join('')
    .slice(0, 2)
    .toUpperCase();
  return (
    <GlassPanel className="relative z-10 m-3.5 hidden w-[220px] flex-none flex-col gap-0.5 overflow-auto px-2.5 py-4 md:flex">
      <div className="px-2.5 pb-3.5 pt-1">
        <Logo />
      </div>
      <nav aria-label={t('nav.primary')} className="flex flex-col gap-0.5">
        {nav.primary.map((item) => (
          <NavLink key={item.to} item={item} />
        ))}
        {nav.tutor.length > 0 && (
          <>
            <div className="px-2.5 pb-1.5 pt-3.5 text-micro font-semibold uppercase tracking-[.06em] text-ink-muted">
              {t('nav.tutorGroup')}
            </div>
            {nav.tutor.map((item) => (
              <NavLink key={item.to} item={item} />
            ))}
          </>
        )}
      </nav>
      <div className="min-h-3 flex-1" />
      <NavLink item={SETTINGS_NAV} />
      <div className="mt-1 flex items-center gap-2.5 border-t border-[var(--glass-stroke)] px-2.5 pt-2.5 text-caption">
        <span className="grid size-7 flex-none place-items-center rounded-full bg-accent-soft text-[12px] font-semibold text-accent-on-soft">
          {initials || 'T'}
        </span>
        <span className="min-w-0 flex-1 truncate text-ink">{displayName}</span>
        <button
          type="button"
          onClick={onSignOut}
          className="rounded-md p-1 text-ink-muted hover:text-ink focus-visible:outline-2 focus-visible:outline-focus-ring"
          aria-label={t('nav.signOut')}
          title={t('nav.signOut')}
        >
          <LogOut className="size-4" aria-hidden="true" />
        </button>
      </div>
    </GlassPanel>
  );
}
