import { useCallback, useEffect, useState, type ReactNode } from 'react'
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import * as DropdownMenu from '@radix-ui/react-dropdown-menu'
import * as Popover from '@radix-ui/react-popover'
import { Command } from 'cmdk'
import {
  Activity, Bell, BookOpen, ClipboardList, Gauge, LayoutDashboard, ListChecks, LogOut, Menu, Monitor, Moon,
  Network, PlusCircle, ScrollText, Search, Send, Settings2, ShieldCheck, Sun, Timer, Users, X,
} from 'lucide-react'
import { api, qs } from '@/lib/api'
import { useAuth, useUser } from '@/lib/auth'
import { useTheme, type ThemePreference } from '@/lib/theme'
import { useLiveUpdates } from '@/lib/live'
import { ago, cn, ROLE_LABEL } from '@/lib/format'
import type { IncidentSummary, NotificationView, Page } from '@/lib/types'
import { Avatar, IconButton, Kbd, Switch } from './ui'
import { PriorityBadge } from './incident-bits'

interface NavItem { to: string; label: string; icon: ReactNode; end?: boolean }

function useNavigation(): { label?: string; items: NavItem[] }[] {
  const user = useUser()
  const { is } = useAuth()
  const ic = 'size-4'

  if (user.role === 'EMPLOYEE') {
    return [{
      items: [
        { to: '/', label: 'Home', icon: <LayoutDashboard className={ic} />, end: true },
        { to: '/report', label: 'Report an incident', icon: <PlusCircle className={ic} /> },
        { to: '/incidents', label: 'My incidents', icon: <ClipboardList className={ic} /> },
      ],
    }]
  }

  const work: NavItem[] = is('TEAM_LEAD')
    ? [
        { to: '/', label: 'Command centre', icon: <Gauge className={ic} />, end: true },
        { to: '/queue', label: 'My queue', icon: <ListChecks className={ic} /> },
        { to: '/incidents', label: 'Incidents', icon: <ClipboardList className={ic} /> },
      ]
    : [
        { to: '/', label: 'My queue', icon: <ListChecks className={ic} />, end: true },
        { to: '/incidents', label: 'All incidents', icon: <ClipboardList className={ic} /> },
      ]
  work.push({ to: '/services', label: 'Service map', icon: <Network className={ic} /> })
  work.push({ to: '/report', label: 'Report an incident', icon: <PlusCircle className={ic} /> })

  const groups: { label?: string; items: NavItem[] }[] = [{ items: work }]
  if (is('TEAM_LEAD')) {
    groups.push({ label: 'Insight', items: [{ to: '/reports', label: 'Reports', icon: <Activity className={ic} /> }] })
  }
  if (is('ADMIN')) {
    groups.push({
      label: 'Administration',
      items: [
        { to: '/admin/users', label: 'People', icon: <Users className={ic} /> },
        { to: '/admin/catalogue', label: 'Services & categories', icon: <BookOpen className={ic} /> },
        { to: '/admin/sla', label: 'SLA policies', icon: <Timer className={ic} /> },
        { to: '/admin/settings', label: 'Automation', icon: <Settings2 className={ic} /> },
        { to: '/admin/audit', label: 'Audit log', icon: <ScrollText className={ic} /> },
        { to: '/admin/outbox', label: 'Delivery log', icon: <Send className={ic} /> },
      ],
    })
  }
  return groups
}

export function Logo({ className }: { className?: string }) {
  return (
    <span className={cn('inline-flex items-center gap-2', className)}>
      <svg viewBox="0 0 32 32" className="size-7" aria-hidden>
        <rect width="32" height="32" rx="8" fill="var(--accent)" />
        <path d="M9 22V10h7.5a4.5 4.5 0 0 1 0 9H13l6 3" fill="none" stroke="var(--accent-ink)" strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round" />
        <circle cx="23.5" cy="10.5" r="2" fill="var(--p1)" />
      </svg>
      <span className="text-[15px] font-semibold tracking-tight">ResolveIQ</span>
    </span>
  )
}

function Sidebar({ onNavigate }: { onNavigate?: () => void }) {
  const groups = useNavigation()
  return (
    <nav aria-label="Main" className="flex h-full flex-col">
      <div className="flex h-14 items-center px-4">
        <Link to="/" onClick={onNavigate}><Logo /></Link>
      </div>
      <div className="flex-1 overflow-y-auto px-2.5 pb-4">
        {groups.map((g, gi) => (
          <div key={gi} className={cn(gi > 0 && 'mt-5')}>
            {g.label && <div className="mb-1 px-2.5 text-xs font-medium text-faint">{g.label}</div>}
            <ul className="space-y-px">
              {g.items.map((item) => (
                <li key={item.to}>
                  <NavLink
                    to={item.to}
                    end={item.end}
                    onClick={onNavigate}
                    className={({ isActive }) => cn(
                      'flex h-8 items-center gap-2.5 rounded-md px-2.5 text-[13.5px] transition-colors',
                      isActive ? 'bg-surface-3 font-medium text-ink' : 'text-ink-2 hover:bg-surface-2 hover:text-ink',
                    )}
                  >
                    {item.icon}
                    {item.label}
                  </NavLink>
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>
      <AvailabilityFooter />
    </nav>
  )
}

/** Technicians can go on/off duty from the sidebar; off-duty people get no new assignments. */
function AvailabilityFooter() {
  const user = useUser()
  const { refresh } = useAuth()
  const qc = useQueryClient()
  const toggle = useMutation({
    mutationFn: (available: boolean) => api.put('/technicians/me/availability', { available }),
    onSuccess: async () => {
      await refresh()
      qc.invalidateQueries({ queryKey: ['dashboard'] })
    },
  })
  if (!user.technician) return null
  const on = user.technician.available
  return (
    <div className="m-2.5 flex items-center justify-between gap-2 rounded-lg border border-line bg-surface px-3 py-2.5">
      <div className="min-w-0">
        <div className="flex items-center gap-1.5 text-[13px] font-medium">
          <span className={cn('size-1.5 rounded-full', on ? 'bg-ok' : 'bg-faint')} />
          {on ? 'On duty' : 'Off duty'}
        </div>
        <div className="text-xs text-muted">{on ? 'Receiving new work' : 'No new assignments'}</div>
      </div>
      <Switch checked={on} onChange={(v) => toggle.mutate(v)} disabled={toggle.isPending} label="Available for assignment" />
    </div>
  )
}

function ThemeMenu() {
  const { preference, resolved, setPreference } = useTheme()
  const options: { value: ThemePreference; label: string; icon: ReactNode }[] = [
    { value: 'light', label: 'Light', icon: <Sun className="size-4" /> },
    { value: 'dark', label: 'Dark', icon: <Moon className="size-4" /> },
    { value: 'system', label: 'Match system', icon: <Monitor className="size-4" /> },
  ]
  return (
    <DropdownMenu.Root>
      <DropdownMenu.Trigger asChild>
        <IconButton label="Change theme">{resolved === 'dark' ? <Moon className="size-4" /> : <Sun className="size-4" />}</IconButton>
      </DropdownMenu.Trigger>
      <DropdownMenu.Portal>
        <DropdownMenu.Content align="end" sideOffset={6} className="z-50 min-w-40 rounded-lg border border-line bg-surface p-1 shadow-pop animate-slide-in">
          <DropdownMenu.RadioGroup value={preference} onValueChange={(v) => setPreference(v as ThemePreference)}>
            {options.map((o) => (
              <DropdownMenu.RadioItem
                key={o.value}
                value={o.value}
                className="flex h-8 cursor-pointer items-center gap-2 rounded-md px-2 text-[13px] text-ink-2 outline-none data-[highlighted]:bg-surface-2 data-[state=checked]:text-ink data-[state=checked]:font-medium"
              >
                {o.icon}{o.label}
                <DropdownMenu.ItemIndicator className="ml-auto size-1.5 rounded-full bg-accent" />
              </DropdownMenu.RadioItem>
            ))}
          </DropdownMenu.RadioGroup>
        </DropdownMenu.Content>
      </DropdownMenu.Portal>
    </DropdownMenu.Root>
  )
}

function NotificationsMenu() {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const [open, setOpen] = useState(false)
  const unread = useQuery({
    queryKey: ['notifications', 'unread'],
    queryFn: () => api.get<{ unread: number }>('/notifications/unread-count'),
    refetchInterval: 60_000,
  })
  const list = useQuery({
    queryKey: ['notifications', 'list'],
    queryFn: () => api.get<Page<NotificationView>>('/notifications?size=15'),
    enabled: open,
  })
  const readAll = useMutation({
    mutationFn: () => api.post('/notifications/read-all'),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['notifications'] }),
  })
  const markRead = useMutation({
    mutationFn: (id: number) => api.post(`/notifications/${id}/read`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['notifications'] }),
  })
  const count = unread.data?.unread ?? 0

  return (
    <Popover.Root open={open} onOpenChange={setOpen}>
      <Popover.Trigger asChild>
        <IconButton label={count ? `Notifications, ${count} unread` : 'Notifications'} className="relative">
          <Bell className="size-4" />
          {count > 0 && (
            <span className="absolute -right-0.5 -top-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-p1 px-1 text-[10px] font-semibold text-white tabular">
              {count > 99 ? '99+' : count}
            </span>
          )}
        </IconButton>
      </Popover.Trigger>
      <Popover.Portal>
        <Popover.Content align="end" sideOffset={6} className="z-50 w-[min(380px,calc(100vw-24px))] rounded-xl border border-line bg-surface shadow-pop animate-slide-in">
          <div className="flex items-center justify-between border-b border-line px-4 py-3">
            <span className="text-sm font-semibold">Notifications</span>
            {count > 0 && (
              <button onClick={() => readAll.mutate()} className="text-[13px] text-accent-text hover:underline cursor-pointer">Mark all as read</button>
            )}
          </div>
          <div className="max-h-[420px] overflow-y-auto">
            {list.isLoading && <div className="p-6 text-center text-[13px] text-muted">Loading…</div>}
            {list.data && list.data.items.length === 0 && (
              <div className="p-8 text-center text-[13px] text-muted">You're all caught up.</div>
            )}
            <ul className="divide-y divide-line">
              {list.data?.items.map((n) => (
                <li key={n.id}>
                  <button
                    onClick={() => {
                      if (!n.read) markRead.mutate(n.id)
                      if (n.incidentNumber) { setOpen(false); navigate(`/incidents/${n.incidentNumber}`) }
                    }}
                    className="flex w-full gap-3 px-4 py-3 text-left hover:bg-surface-2 cursor-pointer"
                  >
                    <span className={cn('mt-1.5 size-1.5 shrink-0 rounded-full', n.read ? 'bg-transparent' : n.urgent ? 'bg-p1' : 'bg-accent')} />
                    <span className="min-w-0">
                      <span className={cn('block text-[13px]', n.read ? 'text-ink-2' : 'font-medium text-ink')}>{n.subject}</span>
                      <span className="mt-0.5 line-clamp-2 block text-xs text-muted">{n.message}</span>
                      <span className="mt-1 block text-[11px] text-faint">{ago(n.createdAt)}</span>
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          </div>
        </Popover.Content>
      </Popover.Portal>
    </Popover.Root>
  )
}

function UserMenu() {
  const user = useUser()
  const { logout } = useAuth()
  const navigate = useNavigate()
  return (
    <DropdownMenu.Root>
      <DropdownMenu.Trigger asChild>
        <button className="flex items-center gap-2 rounded-lg py-1 pl-1 pr-2 hover:bg-surface-2 cursor-pointer" aria-label="Account menu">
          <Avatar name={user.fullName} size={28} />
          <span className="hidden text-left leading-tight md:block">
            <span className="block text-[13px] font-medium">{user.fullName}</span>
            <span className="block text-[11px] text-muted">{ROLE_LABEL[user.role]}</span>
          </span>
        </button>
      </DropdownMenu.Trigger>
      <DropdownMenu.Portal>
        <DropdownMenu.Content align="end" sideOffset={6} className="z-50 min-w-56 rounded-lg border border-line bg-surface p-1 shadow-pop animate-slide-in">
          <div className="px-2.5 py-2">
            <div className="text-[13px] font-medium">{user.fullName}</div>
            <div className="text-xs text-muted">{user.email}</div>
          </div>
          <DropdownMenu.Separator className="my-1 h-px bg-line" />
          <DropdownMenu.Item
            onSelect={() => { logout(); navigate('/login') }}
            className="flex h-8 cursor-pointer items-center gap-2 rounded-md px-2.5 text-[13px] text-ink-2 outline-none data-[highlighted]:bg-surface-2"
          >
            <LogOut className="size-4" /> Sign out
          </DropdownMenu.Item>
        </DropdownMenu.Content>
      </DropdownMenu.Portal>
    </DropdownMenu.Root>
  )
}

// ---------------------------------------------------------------------------------------------
// ⌘K command palette: jump to any incident by number or title, or to any page.
// ---------------------------------------------------------------------------------------------

function CommandPalette({ open, onOpenChange }: { open: boolean; onOpenChange: (o: boolean) => void }) {
  const navigate = useNavigate()
  const groups = useNavigation()
  const [search, setSearch] = useState('')
  const results = useQuery({
    queryKey: ['incidents', 'palette', search],
    queryFn: () => api.get<Page<IncidentSummary>>(`/incidents${qs({ q: search, size: 8 })}`),
    enabled: open && search.trim().length > 1,
    placeholderData: (prev) => prev,
  })
  const go = (to: string) => { onOpenChange(false); setSearch(''); navigate(to) }

  if (!open) return null
  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center bg-black/30 px-4 pt-[14vh] backdrop-blur-[2px]" onClick={() => onOpenChange(false)}>
      <Command
        label="Command palette"
        shouldFilter={false}
        onClick={(e) => e.stopPropagation()}
        onKeyDown={(e) => { if (e.key === 'Escape') onOpenChange(false) }}
        className="w-full max-w-xl overflow-hidden rounded-xl border border-line bg-surface shadow-pop animate-slide-in"
      >
        <div className="flex items-center gap-2 border-b border-line px-4">
          <Search className="size-4 text-faint" />
          <Command.Input
            autoFocus
            value={search}
            onValueChange={setSearch}
            placeholder="Search incidents by number or title, or jump to a page"
            className="h-12 flex-1 bg-transparent text-sm outline-none placeholder:text-faint"
          />
          <Kbd>esc</Kbd>
        </div>
        <Command.List className="max-h-[360px] overflow-y-auto p-1.5">
          <Command.Empty className="px-3 py-6 text-center text-[13px] text-muted">No incidents match “{search}”.</Command.Empty>
          {results.data && results.data.items.length > 0 && (
            <Command.Group heading="Incidents" className="[&_[cmdk-group-heading]]:px-2.5 [&_[cmdk-group-heading]]:py-1.5 [&_[cmdk-group-heading]]:text-xs [&_[cmdk-group-heading]]:text-faint">
              {results.data.items.map((i) => (
                <Command.Item key={i.number} value={i.number} onSelect={() => go(`/incidents/${i.number}`)}
                  className="flex h-10 cursor-pointer items-center gap-3 rounded-md px-2.5 text-[13px] data-[selected=true]:bg-surface-2">
                  <PriorityBadge priority={i.priority} />
                  <span className="tabular text-muted">{i.number}</span>
                  <span className="truncate">{i.title}</span>
                </Command.Item>
              ))}
            </Command.Group>
          )}
          <Command.Group heading="Go to" className="[&_[cmdk-group-heading]]:px-2.5 [&_[cmdk-group-heading]]:py-1.5 [&_[cmdk-group-heading]]:text-xs [&_[cmdk-group-heading]]:text-faint">
            {groups.flatMap((g) => g.items)
              .filter((item) => !search || item.label.toLowerCase().includes(search.toLowerCase()))
              .map((item) => (
                <Command.Item key={item.to} value={item.to} onSelect={() => go(item.to)}
                  className="flex h-9 cursor-pointer items-center gap-2.5 rounded-md px-2.5 text-[13px] text-ink-2 data-[selected=true]:bg-surface-2 data-[selected=true]:text-ink">
                  {item.icon}{item.label}
                </Command.Item>
              ))}
          </Command.Group>
        </Command.List>
      </Command>
    </div>
  )
}

export function AppShell() {
  const [paletteOpen, setPaletteOpen] = useState(false)
  const [mobileNav, setMobileNav] = useState(false)
  const navigate = useNavigate()
  const location = useLocation()

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault()
        setPaletteOpen((o) => !o)
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  useEffect(() => setMobileNav(false), [location.pathname])

  const openIncident = useCallback((n: string) => navigate(`/incidents/${n}`), [navigate])
  useLiveUpdates(true, openIncident)

  return (
    <div className="flex min-h-dvh">
      <aside className="sticky top-0 hidden h-dvh w-[236px] shrink-0 border-r border-line bg-bg lg:block">
        <Sidebar />
      </aside>

      {mobileNav && (
        <div className="fixed inset-0 z-40 lg:hidden">
          <div className="absolute inset-0 bg-black/30" onClick={() => setMobileNav(false)} />
          <aside className="absolute inset-y-0 left-0 w-[260px] border-r border-line bg-bg shadow-pop animate-slide-in">
            <IconButton label="Close menu" className="absolute right-2 top-3" onClick={() => setMobileNav(false)}><X className="size-4" /></IconButton>
            <Sidebar onNavigate={() => setMobileNav(false)} />
          </aside>
        </div>
      )}

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-30 flex h-14 items-center gap-2 border-b border-line bg-bg/85 px-4 backdrop-blur-md sm:px-6">
          <IconButton label="Open menu" className="lg:hidden" onClick={() => setMobileNav(true)}><Menu className="size-4" /></IconButton>
          <button
            onClick={() => setPaletteOpen(true)}
            className="flex h-9 w-full max-w-sm items-center gap-2 rounded-lg border border-line bg-surface px-3 text-[13px] text-faint hover:border-line-strong cursor-pointer"
          >
            <Search className="size-4" />
            <span className="flex-1 text-left">Search incidents…</span>
            <span className="hidden gap-1 sm:flex"><Kbd>⌘</Kbd><Kbd>K</Kbd></span>
          </button>
          <div className="ml-auto flex items-center gap-1">
            <ThemeMenu />
            <NotificationsMenu />
            <div className="mx-1 h-6 w-px bg-line" />
            <UserMenu />
          </div>
        </header>
        <main className="mx-auto w-full max-w-[1240px] flex-1 px-4 py-6 sm:px-8 sm:py-8">
          <Outlet />
        </main>
      </div>

      <CommandPalette open={paletteOpen} onOpenChange={setPaletteOpen} />
    </div>
  )
}

export function RoleGate({ minimum, children }: { minimum: 'TECHNICIAN' | 'TEAM_LEAD' | 'ADMIN'; children: ReactNode }) {
  const { is } = useAuth()
  if (!is(minimum)) {
    return (
      <div className="flex flex-col items-center py-24 text-center">
        <ShieldCheck className="mb-3 size-8 text-faint" />
        <p className="font-medium">This area is for {ROLE_LABEL[minimum].toLowerCase()}s and above</p>
        <Link to="/" className="mt-3 text-[13px] text-accent-text hover:underline">Back to home</Link>
      </div>
    )
  }
  return <>{children}</>
}
