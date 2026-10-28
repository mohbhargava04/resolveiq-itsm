import { Link } from 'react-router-dom'
import { AlertTriangle, ArrowUpRight, Copy, Pause, Sparkles } from 'lucide-react'
import { cn, duration, PRIORITY_LABEL, remaining, SLA_LABEL, STATUS_LABEL, when } from '@/lib/format'
import type { EscalationLevel, IncidentStatus, IncidentSummary, Priority, SlaRisk, SlaStatus } from '@/lib/types'
import { Avatar } from './ui'

// ---------------------------------------------------------------------------------------------
// Priority — the loudest element in the interface, by design.
// ---------------------------------------------------------------------------------------------

const P_STYLE: Record<Priority, string> = {
  P1: 'bg-p1 text-white',
  P2: 'bg-p2-soft text-p2 ring-1 ring-inset ring-p2/30',
  P3: 'bg-p3-soft text-p3',
  P4: 'bg-p4-soft text-p4',
}

export function PriorityBadge({ priority, showLabel = false, className }: { priority?: Priority; showLabel?: boolean; className?: string }) {
  if (!priority) return <span className="text-xs text-faint">—</span>
  return (
    <span
      title={`${priority} ${PRIORITY_LABEL[priority]}`}
      className={cn('inline-flex h-[22px] items-center gap-1 rounded-md px-1.5 text-xs font-semibold tabular', P_STYLE[priority], className)}
    >
      {priority}
      {showLabel && <span className="font-medium opacity-90">{PRIORITY_LABEL[priority]}</span>}
    </span>
  )
}

export function priorityColor(p?: Priority) {
  return p === 'P1' ? 'var(--p1)' : p === 'P2' ? 'var(--p2)' : p === 'P3' ? 'var(--p3)' : 'var(--p4)'
}

// ---------------------------------------------------------------------------------------------
// Status — quiet: a dot and a word. Only "awaiting info" and "resolved" get a hint of colour,
// because they are the two states where the reporter has to act.
// ---------------------------------------------------------------------------------------------

const STATUS_DOT: Record<IncidentStatus, string> = {
  NEW: 'bg-faint',
  CLASSIFIED: 'bg-faint',
  ASSIGNED: 'bg-p3',
  ACKNOWLEDGED: 'bg-p3',
  IN_PROGRESS: 'bg-accent',
  AWAITING_INFO: 'bg-p2',
  RESOLVED: 'bg-ok',
  VERIFIED: 'bg-ok',
  CLOSED: 'bg-line-strong',
}

export function StatusBadge({ status, className }: { status: IncidentStatus; className?: string }) {
  return (
    <span className={cn('inline-flex items-center gap-1.5 text-[13px] text-ink-2 whitespace-nowrap', className)}>
      <span className={cn('size-1.5 rounded-full', STATUS_DOT[status])} />
      {STATUS_LABEL[status]}
    </span>
  )
}

// ---------------------------------------------------------------------------------------------
// The SLA fuse: the signature element. A hairline that burns down as the resolution window is used.
// ---------------------------------------------------------------------------------------------

function fuseColor(status: SlaStatus, fraction: number) {
  if (status === 'BREACHED') return 'var(--p1)'
  if (status === 'MET') return 'var(--ok)'
  if (status === 'PAUSED') return 'var(--faint)'
  if (status === 'AT_RISK' || fraction >= 0.75) return 'var(--p2)'
  return 'var(--accent)'
}

export function SlaFuse({ status, elapsedPercent, minutesRemaining, compact = false, className }: {
  status: SlaStatus; elapsedPercent?: number; minutesRemaining?: number; compact?: boolean; className?: string
}) {
  if (status === 'NOT_STARTED') return <span className="text-xs text-faint">No SLA yet</span>
  const fraction = Math.max(0, Math.min(1, (elapsedPercent ?? 0) / 100))
  const color = fuseColor(status, fraction)
  const live = status === 'AT_RISK' || (status === 'BREACHED' && minutesRemaining !== undefined)
  const label =
    status === 'MET' ? 'Met' :
    status === 'PAUSED' ? 'Paused' :
    minutesRemaining !== undefined ? remaining(minutesRemaining) : SLA_LABEL[status]

  return (
    <div className={cn('min-w-0', className)} title={`SLA ${SLA_LABEL[status].toLowerCase()}`}>
      <div className={cn('flex items-center justify-between gap-2', compact ? 'mb-1' : 'mb-1.5')}>
        <span className={cn('flex items-center gap-1 tabular whitespace-nowrap', compact ? 'text-xs' : 'text-[13px]')} style={{ color: status === 'ON_TRACK' ? 'var(--ink-2)' : color }}>
          {status === 'PAUSED' && <Pause className="size-3" />}
          {status === 'BREACHED' && <AlertTriangle className="size-3" />}
          {label}
        </span>
      </div>
      <div className={cn('relative w-full overflow-hidden rounded-full bg-surface-3', compact ? 'h-[3px]' : 'h-1')}>
        <div
          className={cn('absolute inset-y-0 left-0 rounded-full', live && 'fuse-live')}
          style={{ width: `${Math.max(3, fraction * 100)}%`, background: color }}
        />
      </div>
    </div>
  )
}

/** For list rows, where only minutesRemaining is known: estimate the fraction from the status. */
export function SummaryFuse({ incident }: { incident: IncidentSummary }) {
  const active = !['RESOLVED', 'VERIFIED', 'CLOSED'].includes(incident.status)
  if (!active) {
    return <span className="text-xs text-muted">{incident.slaStatus === 'BREACHED' ? 'Breached' : SLA_LABEL[incident.slaStatus]}</span>
  }
  const elapsed = estimateElapsed(incident)
  return <SlaFuse compact status={incident.slaStatus} minutesRemaining={incident.minutesRemaining} elapsedPercent={elapsed} />
}

function estimateElapsed(i: IncidentSummary) {
  if (!i.resolutionDueAt) return 0
  const start = new Date(i.createdAt).getTime()
  const due = new Date(i.resolutionDueAt).getTime()
  const now = Date.now()
  if (due <= start) return 100
  return Math.round(((now - start) / (due - start)) * 100)
}

export function RiskTag({ risk }: { risk?: SlaRisk }) {
  if (!risk || risk.level === 'NONE' || risk.level === 'LOW') return null
  const map = {
    MEDIUM: 'text-p2 bg-p2-soft',
    HIGH: 'text-p1 bg-p1-soft',
    BREACHED: 'text-white bg-p1',
    PAUSED: 'text-muted bg-surface-2',
  } as const
  const text = { MEDIUM: 'Some risk', HIGH: 'High risk', BREACHED: 'Breached', PAUSED: 'Paused' } as const
  const lvl = risk.level as keyof typeof map
  return (
    <span title={risk.factors.join('\n')} className={cn('inline-flex h-5 shrink-0 items-center whitespace-nowrap rounded px-1.5 text-[11px] font-semibold', map[lvl])}>
      {text[lvl]}
    </span>
  )
}

export function EscalationTag({ level }: { level: EscalationLevel }) {
  if (level === 'NONE') return null
  const label = { TEAM_LEAD: 'Escalated to lead', ADMINISTRATOR: 'Escalated to admin', IT_MANAGER: 'Escalated to manager' }[level]
  return (
    <span className="inline-flex items-center gap-1 text-[11px] font-medium text-p1">
      <ArrowUpRight className="size-3" />
      {label}
    </span>
  )
}

// ---------------------------------------------------------------------------------------------
// Incident list — a divided list rather than a card grid; dense but calm.
// ---------------------------------------------------------------------------------------------

export function IncidentList({ items, showReporter = false, showAssignee = true, empty, riskByNumber }: {
  items: IncidentSummary[]; showReporter?: boolean; showAssignee?: boolean; empty?: React.ReactNode
  riskByNumber?: Record<string, SlaRisk>
}) {
  if (!items.length) return <>{empty}</>
  return (
    <ul className="divide-y divide-line">
      {items.map((i) => (
        <li key={i.number}>
          <Link
            to={`/incidents/${i.number}`}
            className="group grid grid-cols-[auto_1fr_auto] items-center gap-x-4 gap-y-1 px-1 py-3 transition-colors hover:bg-surface-2/60 sm:grid-cols-[auto_minmax(0,1fr)_150px_140px] rounded-md"
          >
            <PriorityBadge priority={i.priority} className="w-8 justify-center" />
            <div className="min-w-0">
              <div className="flex items-center gap-2">
                <span className="truncate font-medium text-ink group-hover:text-accent-text">{i.title}</span>
                {i.suspectedDuplicateOf && (
                  <span title={`Possible duplicate of ${i.suspectedDuplicateOf}`} className="text-faint"><Copy className="size-3.5" /></span>
                )}
                {i.autoClassified && (
                  <span title="Categorised by AI" className="text-faint"><Sparkles className="size-3.5" /></span>
                )}
                {riskByNumber?.[i.number] && <RiskTag risk={riskByNumber[i.number]} />}
              </div>
              <div className="mt-0.5 flex flex-wrap items-center gap-x-3 gap-y-0.5 text-xs text-muted">
                <span className="tabular font-medium text-ink-2">{i.number}</span>
                <span>{i.service.name}</span>
                {showReporter && <span>by {i.reporter.name}</span>}
                <span className="tabular">{when(i.createdAt)}</span>
                <EscalationTag level={i.escalationLevel} />
              </div>
            </div>
            <div className="hidden sm:block">
              <StatusBadge status={i.status} />
              {showAssignee && (
                <div className="mt-1 flex items-center gap-1.5 text-xs text-muted">
                  {i.assignee ? <><Avatar name={i.assignee.name} size={16} />{i.assignee.name}</> : <span className="text-p2">Unassigned</span>}
                </div>
              )}
            </div>
            <div className="col-span-3 sm:col-span-1">
              <SummaryFuse incident={i} />
            </div>
          </Link>
        </li>
      ))}
    </ul>
  )
}

export function MiniStat({ label, value, sub, tone }: { label: string; value: React.ReactNode; sub?: React.ReactNode; tone?: 'p1' | 'p2' | 'ok' }) {
  const color = tone === 'p1' ? 'text-p1' : tone === 'p2' ? 'text-p2' : tone === 'ok' ? 'text-ok' : 'text-ink'
  return (
    <div className="min-w-0 bg-surface px-5 py-4">
      <div className="text-[13px] text-muted">{label}</div>
      <div className={cn('mt-1 text-[26px] font-semibold leading-none tabular tracking-tight', color)}>{value}</div>
      {sub && <div className="mt-1.5 text-xs text-muted">{sub}</div>}
    </div>
  )
}

/** A row of stats sharing one frame, separated by hairlines (gap-px over the line colour). */
export function StatStrip({ children, className }: { children: React.ReactNode; className?: string }) {
  return (
    <div className={cn('grid gap-px overflow-hidden rounded-xl border border-line bg-line', className)}>
      {children}
    </div>
  )
}

export { duration }
