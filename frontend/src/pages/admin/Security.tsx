import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { CheckCircle2, CircleDashed, ShieldCheck } from 'lucide-react'
import { api } from '@/lib/api'
import { cn, fullDate, humanise } from '@/lib/format'
import type { AuditEntry, Page, SecurityOverview } from '@/lib/types'
import { PageHeader, Panel, Skeleton } from '@/components/ui'
import { MiniStat, StatStrip } from '@/components/incident-bits'

const ALERT = new Set(['ACCOUNT_LOCKED', 'SESSION_REUSE_DETECTED', 'SSO_LOGIN_REJECTED', 'LOGIN_FAILED', 'MFA_FAILED'])

/** Security posture at a glance: what protects the system, and what has happened recently. */
export default function Security() {
  const o = useQuery({ queryKey: ['admin', 'security'], queryFn: () => api.get<SecurityOverview>('/admin/security'), refetchInterval: 30_000 })
  const events = useQuery({ queryKey: ['admin', 'security', 'events'], queryFn: () => api.get<Page<AuditEntry>>('/audit?security=true&size=25'), refetchInterval: 30_000 })
  const recent = events.data?.items ?? []
  const d = o.data
  const mfaPct = d && d.privilegedAccounts ? Math.round((d.privilegedWithMfa / d.privilegedAccounts) * 100) : 0

  const controls: { label: string; detail: string; on: boolean | undefined; link?: string }[] = [
    { label: 'Company sign-in (Google)', detail: d?.googleSignInEnabled ? 'OpenID Connect with PKCE' : 'Add ITSM_GOOGLE_CLIENT_ID and _SECRET to turn on', on: d?.googleSignInEnabled },
    { label: 'Company e-mail domains only', detail: d?.allowedDomains.length ? d.allowedDomains.map((x) => '@' + x).join(', ') : 'Any domain is allowed', on: !!d?.allowedDomains.length, link: '/admin/settings' },
    { label: '2FA required for admins and managers', detail: `${d?.privilegedWithMfa ?? 0} of ${d?.privilegedAccounts ?? 0} have it on`, on: d?.mfaRequiredForPrivileged, link: '/admin/settings' },
    { label: 'Short-lived tokens, rotating refresh cookie', detail: '15-minute access token in memory; HttpOnly, SameSite=Strict cookie', on: true },
    { label: 'Brute-force protection', detail: 'Lockout after 5 wrong attempts + per-address rate limits', on: true },
    { label: 'Strict Content-Security-Policy', detail: 'No inline or third-party scripts', on: true },
    { label: 'Upload scanning', detail: 'Content-type allow-list + malware signature check', on: true },
  ]

  return (
    <div className="space-y-6">
      <PageHeader title="Security" description="How accounts and data are protected, and recent security events. See docs/SECURITY.md for the OWASP Top 10 mapping." />
      <StatStrip className="grid-cols-2 md:grid-cols-4">
        <MiniStat label="Active sessions" value={d?.activeSessions ?? '–'} sub="Signed-in browsers" />
        <MiniStat label="Failed sign-ins (24 h)" value={d?.failedLogins24h ?? '–'} sub={d ? `${d.lockouts24h} lockouts` : undefined} tone={d && d.lockouts24h > 0 ? 'p2' : undefined} />
        <MiniStat label="Admin 2FA adoption" value={d ? `${mfaPct}%` : '–'} sub={d ? `${d.privilegedWithMfa} of ${d.privilegedAccounts} accounts` : undefined} tone={d && mfaPct < 100 ? 'p2' : 'ok'} />
        <MiniStat label="Stolen-token alerts (30 d)" value={d?.tokenReuse30d ?? '–'} sub="Refresh token replayed" tone={d && d.tokenReuse30d > 0 ? 'p1' : undefined} />
      </StatStrip>

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.2fr)]">
        <Panel title="Protections">
          {!d ? <Skeleton className="h-64" /> : (
            <ul className="space-y-3">
              {controls.map((c) => (
                <li key={c.label} className="flex gap-3">
                  {c.on ? <CheckCircle2 className="mt-0.5 size-4 shrink-0 text-ok" /> : <CircleDashed className="mt-0.5 size-4 shrink-0 text-faint" />}
                  <div className="min-w-0">
                    <p className="text-[13.5px] font-medium">{c.link ? <Link to={c.link} className="hover:text-accent-text">{c.label}</Link> : c.label}</p>
                    <p className="text-xs text-muted">{c.detail}</p>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </Panel>
        <Panel title="Recent security events" action={<Link to="/admin/audit" className="text-[13px] font-medium text-accent-text hover:underline">Full audit log</Link>}>
          {events.isLoading ? <Skeleton className="h-64" /> : recent.length === 0 ? (
            <p className="flex items-center gap-2 py-6 text-[13px] text-muted"><ShieldCheck className="size-4" /> Nothing to report.</p>
          ) : (
            <ol className="divide-y divide-line">
              {recent.map((e) => (
                <li key={e.id} className="py-2.5">
                  <p className="flex items-baseline justify-between gap-3 text-[13px]">
                    <span className={cn('font-medium', ALERT.has(e.action) && 'text-p1')}>{humanise(e.action)}</span>
                    <span className="shrink-0 text-xs tabular text-faint">{fullDate(e.occurredAt)}</span>
                  </p>
                  <p className="mt-0.5 text-xs text-muted">{e.actor} — {e.description}</p>
                </li>
              ))}
            </ol>
          )}
        </Panel>
      </div>
    </div>
  )
}
