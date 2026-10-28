import { useState, type ReactNode } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import {
  ArrowUpRight, BookOpenCheck, ChevronRight, CircleCheck, Copy, Hand, Lightbulb, Lock, MessageSquare, Play,
  RotateCcw, Sparkles, Star, UserPlus, Wrench,
} from 'lucide-react'
import { api, ApiError } from '@/lib/api'
import { useUser } from '@/lib/auth'
import { ago, cn, duration, fullDate, humanise, LEVEL_LABEL, remaining, when } from '@/lib/format'
import type {
  AuditEntry, IncidentAction, IncidentDetail as Incident, NoteView, Priority, SimilarIncident, SlaRisk, SuggestedResolution,
} from '@/lib/types'
import { Avatar, Button, Dialog, EmptyState, ErrorNote, Field, Input, Select, Skeleton, Textarea } from '@/components/ui'
import { EscalationTag, PriorityBadge, RiskTag, SlaFuse, StatusBadge } from '@/components/incident-bits'
import { AssignDialog } from '@/components/AssignDialog'

type DialogKind = 'assign' | 'requestInfo' | 'resolve' | 'reopen' | 'escalate' | 'reprioritise' | 'duplicate' | null

export default function IncidentDetail() {
  const { number = '' } = useParams()
  const user = useUser()
  const qc = useQueryClient()
  const [dialog, setDialog] = useState<DialogKind>(null)
  const [prefill, setPrefill] = useState<{ summary?: string; rootCause?: string }>({})

  const incident = useQuery({ queryKey: ['incident', number], queryFn: () => api.get<Incident>(`/incidents/${number}`) })
  const staff = user.role !== 'EMPLOYEE'

  const action = useMutation({
    mutationFn: ({ path, body }: { path: string; body?: unknown; success: string }) => api.post<Incident>(`/incidents/${number}/${path}`, body),
    onSuccess: (data, vars) => {
      qc.setQueryData(['incident', number], data)
      qc.invalidateQueries({ queryKey: ['incident', number, 'audit'] })
      qc.invalidateQueries({ queryKey: ['incidents'] })
      qc.invalidateQueries({ queryKey: ['dashboard'] })
      toast.success(vars.success)
      setDialog(null)
    },
    onError: (err) => toast.error(err instanceof Error ? err.message : 'Action failed'),
  })
  const run = (path: string, success: string, body?: unknown) => action.mutate({ path, body, success })

  if (incident.isLoading) return <DetailSkeleton />
  if (incident.error) {
    const notFound = incident.error instanceof ApiError && incident.error.status === 404
    return (
      <EmptyState title={notFound ? `${number} doesn't exist or isn't visible to you` : 'Could not load this incident'}
        action={<Link to="/incidents"><Button>Back to incidents</Button></Link>}>
        {notFound ? 'Employees can only open incidents they reported.' : (incident.error as Error).message}
      </EmptyState>
    )
  }
  const i = incident.data!
  const can = (a: IncidentAction) => i.availableActions.includes(a)

  return (
    <div>
      <nav aria-label="Breadcrumb" className="mb-3 flex items-center gap-1 text-[13px] text-muted">
        <Link to="/incidents" className="hover:text-ink">Incidents</Link>
        <ChevronRight className="size-3.5" />
        <span className="tabular text-ink-2">{i.number}</span>
      </nav>

      <header className="mb-6">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="min-w-0 max-w-3xl">
            <h1 className="text-[24px] font-semibold leading-tight text-balance">{i.title}</h1>
            <div className="mt-2.5 flex flex-wrap items-center gap-x-4 gap-y-2 text-[13px] text-muted">
              <PriorityBadge priority={i.priority} showLabel />
              <StatusBadge status={i.status} />
              <span>{i.service.name}</span>
              {i.category && (
                <span className="inline-flex items-center gap-1">
                  {i.autoClassified && <Sparkles className="size-3.5 text-accent-text" aria-label="Categorised by AI" />}
                  {i.category.name}
                </span>
              )}
              <span>Reported by {i.reporter.name} {ago(i.createdAt)}</span>
              <EscalationTag level={i.escalation.level} />
            </div>
          </div>
          <ActionBar i={i} can={can} busy={action.isPending} run={run} open={setDialog} />
        </div>
      </header>

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_320px]">
        <div className="min-w-0 space-y-6">
          <SlaPanel i={i} />

          {i.duplicateOf && <DuplicateNotice i={i} canAct={can('MARK_DUPLICATE')} onConfirm={() => setDialog('duplicate')} onDismiss={() => run('duplicate/dismiss', 'Duplicate warning dismissed')} />}

          {i.status === 'AWAITING_INFO' && can('PROVIDE_INFO') && <ProvideInfo i={i} busy={action.isPending} onSend={(text) => run('provide-info', 'Thanks — the technician has been notified', { text })} />}

          {i.resolution && <ResolutionBox i={i} />}

          {can('FEEDBACK') && <FeedbackBox busy={action.isPending} onSubmit={(rating, comment) => run('feedback', 'Thanks for your feedback', { rating, comment })} />}

          <section className="rounded-xl border border-line bg-surface">
            <div className="px-5 pt-4">
              <h2 className="text-[15px] font-semibold">Description</h2>
              <p className="mt-2 whitespace-pre-wrap text-[14px] leading-relaxed text-ink-2">{i.description}</p>
            </div>
            <ActivityTabs i={i} staff={staff} canNote={can('ADD_NOTE')} canInternal={can('ADD_INTERNAL_NOTE')} />
          </section>
        </div>

        <aside className="space-y-4">
          <DetailsCard i={i} />
          {staff && i.status !== 'CLOSED' && <RiskCard number={i.number} />}
          {staff && <SuggestionsCard number={i.number} canResolve={can('RESOLVE')} onUse={(r) => { setPrefill({ summary: r.summary, rootCause: r.rootCause }); setDialog('resolve') }} />}
          {staff && i.status !== 'CLOSED' && <SimilarCard number={i.number} />}
        </aside>
      </div>

      {dialog === 'assign' && <AssignDialog number={i.number} open onOpenChange={(o) => !o && setDialog(null)} />}
      <TextDialog open={dialog === 'requestInfo'} onClose={() => setDialog(null)} busy={action.isPending}
        title="Ask the reporter for information" description="The SLA clock pauses until they reply." label="Question" submit="Send question"
        onSubmit={(text) => run('request-info', 'Question sent; SLA paused', { text })} />
      <TextDialog open={dialog === 'reopen'} onClose={() => setDialog(null)} busy={action.isPending}
        title="Reopen this incident" description="Tell the technician what still isn't working." label="What's still wrong?" submit="Reopen"
        onSubmit={(text) => run('reopen', 'Incident reopened', { text })} />
      <TextDialog open={dialog === 'escalate'} onClose={() => setDialog(null)} busy={action.isPending} optional
        title="Escalate one level" description={`Currently with: ${i.escalation.label}.`} label="Reason" submit="Escalate"
        onSubmit={(text) => run('escalate', 'Escalated', { text: text || undefined })} />
      <ResolveDialog open={dialog === 'resolve'} prefill={prefill} onClose={() => { setDialog(null); setPrefill({}) }} busy={action.isPending}
        onSubmit={(summary, rootCause) => run('resolve', 'Resolved; the reporter has been asked to confirm', { summary, rootCause })} />
      <ReprioritiseDialog open={dialog === 'reprioritise'} current={i.priority} onClose={() => setDialog(null)} busy={action.isPending}
        onSubmit={(priority, reason) => run('reprioritise', 'Priority updated', { priority, reason })} />
      <DuplicateDialog open={dialog === 'duplicate'} initial={i.duplicateOf?.number ?? ''} onClose={() => setDialog(null)} busy={action.isPending}
        onSubmit={(parentNumber) => run('duplicate', `Closed as a duplicate of ${parentNumber}`, { parentNumber })} />
    </div>
  )
}

// ---------------------------------------------------------------------------------------------

function ActionBar({ i, can, busy, run, open }: {
  i: Incident; can: (a: IncidentAction) => boolean; busy: boolean
  run: (path: string, success: string, body?: unknown) => void; open: (d: DialogKind) => void
}) {
  const primary: ReactNode[] = []
  const secondary: ReactNode[] = []
  if (can('ACKNOWLEDGE')) primary.push(<Button key="ack" variant="primary" icon={<Hand className="size-4" />} loading={busy} onClick={() => run('acknowledge', 'Acknowledged; response SLA met')}>Acknowledge</Button>)
  if (can('START_WORK')) primary.push(<Button key="start" variant="primary" icon={<Play className="size-4" />} loading={busy} onClick={() => run('start', 'Investigation started')}>Start work</Button>)
  if (can('RESOLVE')) primary.push(<Button key="resolve" variant="primary" icon={<Wrench className="size-4" />} onClick={() => open('resolve')}>Resolve</Button>)
  if (can('VERIFY')) primary.push(<Button key="verify" variant="primary" icon={<CircleCheck className="size-4" />} loading={busy} onClick={() => run('verify', 'Thanks — confirmed as fixed')}>It's fixed</Button>)
  if (can('CLOSE')) primary.push(<Button key="close" variant="primary" loading={busy} onClick={() => run('close', 'Incident closed')}>Close incident</Button>)
  if (can('ASSIGN')) secondary.push(<Button key="assign" icon={<UserPlus className="size-4" />} onClick={() => open('assign')}>{i.assignee ? 'Reassign' : 'Assign'}</Button>)
  if (can('REQUEST_INFO')) secondary.push(<Button key="ask" icon={<MessageSquare className="size-4" />} onClick={() => open('requestInfo')}>Ask reporter</Button>)
  if (can('REOPEN')) secondary.push(<Button key="reopen" icon={<RotateCcw className="size-4" />} onClick={() => open('reopen')}>Still broken</Button>)
  if (can('ESCALATE')) secondary.push(<Button key="esc" variant="ghost" icon={<ArrowUpRight className="size-4" />} onClick={() => open('escalate')}>Escalate</Button>)
  if (can('REPRIORITISE')) secondary.push(<Button key="prio" variant="ghost" onClick={() => open('reprioritise')}>Change priority</Button>)
  if (can('MARK_DUPLICATE')) secondary.push(<Button key="dup" variant="ghost" icon={<Copy className="size-4" />} onClick={() => open('duplicate')}>Duplicate</Button>)
  if (!primary.length && !secondary.length) return null
  return <div className="flex flex-wrap items-center gap-2">{secondary}{primary}</div>
}

function SlaPanel({ i }: { i: Incident }) {
  const s = i.sla
  if (s.status === 'NOT_STARTED') return null
  const responseDone = !!s.respondedAt
  const responseRemaining = s.responseDueAt ? Math.round((new Date(s.responseDueAt).getTime() - Date.now()) / 60000) : undefined
  return (
    <section className="rounded-xl border border-line bg-surface px-5 py-4" aria-label="Service level">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 className="text-[15px] font-semibold">Service level</h2>
        <span className="text-xs text-muted">{s.policyName}{s.paused && ' — paused while waiting for the reporter'}</span>
      </div>
      <div className="mt-4 grid gap-5 sm:grid-cols-2">
        <div>
          <div className="mb-1 flex justify-between text-xs text-muted">
            <span>First response within {duration(s.responseMinutes)}</span>
            <span className="tabular">due {when(s.responseDueAt)}</span>
          </div>
          {responseDone ? (
            <p className={cn('text-[13px] font-medium', s.responseBreached ? 'text-p1' : 'text-ok')}>
              {s.responseBreached ? 'Late' : 'Met'}: acknowledged after {duration(s.responseMinutesTaken)}
            </p>
          ) : (
            <SlaFuse status={s.responseBreached ? 'BREACHED' : (responseRemaining ?? 0) < (s.responseMinutes ?? 1) * 0.25 ? 'AT_RISK' : 'ON_TRACK'}
              minutesRemaining={responseRemaining}
              elapsedPercent={s.responseMinutes ? 100 - ((responseRemaining ?? 0) / s.responseMinutes) * 100 : 0} />
          )}
        </div>
        <div>
          <div className="mb-1 flex justify-between text-xs text-muted">
            <span>Resolution within {duration(s.resolutionMinutes)}</span>
            <span className="tabular">due {when(s.resolutionDueAt)}</span>
          </div>
          {s.resolvedAt ? (
            <p className={cn('text-[13px] font-medium', s.resolutionBreached ? 'text-p1' : 'text-ok')}>
              {s.resolutionBreached ? 'Late' : 'Met'}: resolved in {duration(s.resolutionMinutesTaken)}
            </p>
          ) : (
            <SlaFuse status={s.status} elapsedPercent={s.elapsedPercent} minutesRemaining={s.minutesRemaining} />
          )}
        </div>
      </div>
    </section>
  )
}

function DuplicateNotice({ i, canAct, onConfirm, onDismiss }: { i: Incident; canAct: boolean; onConfirm: () => void; onDismiss: () => void }) {
  const d = i.duplicateOf!
  return (
    <section className={cn('rounded-xl border px-5 py-4', d.confirmed ? 'border-line bg-surface-2' : 'border-p2/40 bg-surface')}>
      <div className="flex flex-wrap items-start gap-3">
        <Copy className={cn('mt-0.5 size-4', d.confirmed ? 'text-muted' : 'text-p2')} />
        <div className="min-w-0 flex-1">
          <p className="text-[13.5px] font-medium">
            {d.confirmed ? 'Merged into ' : 'Looks like '}
            <Link to={`/incidents/${d.number}`} className="text-accent-text hover:underline">{d.number}</Link>
            {!d.confirmed && d.score !== undefined && <span className="font-normal text-muted"> ({Math.round(d.score * 100)}% similar)</span>}
          </p>
          <p className="mt-0.5 truncate text-[13px] text-muted">{d.title}</p>
          {d.confirmed && <p className="mt-1 text-xs text-muted">Progress is tracked on {d.number}; you'll be notified when it is resolved.</p>}
        </div>
        {canAct && !d.confirmed && (
          <div className="flex gap-2">
            <Button size="sm" variant="ghost" onClick={onDismiss}>Not a duplicate</Button>
            <Button size="sm" onClick={onConfirm}>Merge</Button>
          </div>
        )}
      </div>
    </section>
  )
}

function ProvideInfo({ i, busy, onSend }: { i: Incident; busy: boolean; onSend: (t: string) => void }) {
  const [text, setText] = useState('')
  const question = [...i.notes].reverse().find((n) => n.type === 'INFO_REQUEST')
  return (
    <section className="rounded-xl border border-p2/40 bg-surface px-5 py-4">
      <h2 className="text-[15px] font-semibold">{question?.author ?? 'The technician'} needs more information</h2>
      {question && <blockquote className="mt-2 border-l-2 border-p2 pl-3 text-[14px] text-ink-2">{question.content}</blockquote>}
      <form className="mt-3 space-y-2" onSubmit={(e) => { e.preventDefault(); if (text.trim()) onSend(text.trim()) }}>
        <Textarea rows={3} value={text} onChange={(e) => setText(e.target.value)} placeholder="Your answer" aria-label="Your answer" />
        <div className="flex justify-end"><Button type="submit" variant="primary" loading={busy} disabled={!text.trim()}>Send reply</Button></div>
      </form>
    </section>
  )
}

function ResolutionBox({ i }: { i: Incident }) {
  const r = i.resolution!
  return (
    <section className="rounded-xl border border-ok/30 bg-ok-soft/50 px-5 py-4">
      <h2 className="flex items-center gap-2 text-[15px] font-semibold"><CircleCheck className="size-4 text-ok" /> How it was fixed</h2>
      <p className="mt-2 whitespace-pre-wrap text-[14px] leading-relaxed">{r.summary}</p>
      {r.rootCause && <p className="mt-2 text-[13px] text-muted"><span className="font-medium text-ink-2">Root cause:</span> {r.rootCause}</p>}
      <p className="mt-2 text-xs text-muted">{r.resolvedBy?.name ?? 'Resolved'} {r.resolvedAt ? `on ${fullDate(r.resolvedAt)}` : ''}</p>
      {i.feedback && (
        <p className="mt-3 flex items-center gap-1 text-[13px]">
          {[1, 2, 3, 4, 5].map((n) => <Star key={n} className={cn('size-4', n <= i.feedback!.rating ? 'fill-p2 text-p2' : 'text-line-strong')} />)}
          <span className="ml-1 text-muted">{i.feedback.comment ? `“${i.feedback.comment}”` : 'Rated by the reporter'}</span>
        </p>
      )}
    </section>
  )
}

function FeedbackBox({ busy, onSubmit }: { busy: boolean; onSubmit: (rating: number, comment?: string) => void }) {
  const [rating, setRating] = useState(0)
  const [hover, setHover] = useState(0)
  const [comment, setComment] = useState('')
  const labels = ['', 'Poor', 'Below expectations', 'Okay', 'Good', 'Excellent']
  return (
    <section className="rounded-xl border border-line bg-surface px-5 py-4">
      <h2 className="text-[15px] font-semibold">How did we do?</h2>
      <div className="mt-2 flex items-center gap-1" role="radiogroup" aria-label="Rating">
        {[1, 2, 3, 4, 5].map((n) => (
          <button key={n} type="button" role="radio" aria-checked={rating === n} aria-label={`${n} star${n > 1 ? 's' : ''}`}
            onMouseEnter={() => setHover(n)} onMouseLeave={() => setHover(0)} onClick={() => setRating(n)} className="cursor-pointer p-0.5">
            <Star className={cn('size-6 transition-colors', n <= (hover || rating) ? 'fill-p2 text-p2' : 'text-line-strong')} />
          </button>
        ))}
        <span className="ml-2 text-[13px] text-muted">{labels[hover || rating]}</span>
      </div>
      {rating > 0 && (
        <div className="mt-3 space-y-2">
          <Textarea rows={2} value={comment} onChange={(e) => setComment(e.target.value)} placeholder="Anything we should know? (optional)" aria-label="Comment" />
          <div className="flex justify-end"><Button variant="primary" loading={busy} onClick={() => onSubmit(rating, comment || undefined)}>Send feedback</Button></div>
        </div>
      )}
    </section>
  )
}

function ActivityTabs({ i, staff, canNote, canInternal }: { i: Incident; staff: boolean; canNote: boolean; canInternal: boolean }) {
  const [tab, setTab] = useState<'activity' | 'history'>('activity')
  return (
    <div className="mt-5 border-t border-line">
      <div role="tablist" className="flex gap-4 px-5">
        {(['activity', 'history'] as const).map((t) => (
          <button key={t} role="tab" aria-selected={tab === t} onClick={() => setTab(t)}
            className={cn('-mb-px border-b-2 py-3 text-[13.5px] font-medium transition-colors cursor-pointer',
              tab === t ? 'border-accent text-ink' : 'border-transparent text-muted hover:text-ink')}>
            {t === 'activity' ? `Conversation and work log (${i.notes.length})` : 'Audit history'}
          </button>
        ))}
      </div>
      <div className="px-5 pb-5 pt-4">
        {tab === 'activity' ? <Notes i={i} staff={staff} canNote={canNote} canInternal={canInternal} /> : <History number={i.number} />}
      </div>
    </div>
  )
}

const NOTE_LABEL: Record<NoteView['type'], string> = {
  INVESTIGATION: 'Investigation', COMMENT: 'Comment', INFO_REQUEST: 'Asked the reporter', INFO_RESPONSE: 'Reporter replied',
  RESOLUTION: 'Resolution', SYSTEM: 'System',
}

function Notes({ i, staff, canNote, canInternal }: { i: Incident; staff: boolean; canNote: boolean; canInternal: boolean }) {
  const qc = useQueryClient()
  const [text, setText] = useState('')
  const [internal, setInternal] = useState(false)
  const add = useMutation({
    mutationFn: () => api.post<Incident>(`/incidents/${i.number}/notes`, {
      type: staff ? 'INVESTIGATION' : 'COMMENT', content: text.trim(), internal: canInternal && internal,
    }),
    onSuccess: (d) => {
      qc.setQueryData(['incident', i.number], d)
      qc.invalidateQueries({ queryKey: ['incident', i.number, 'audit'] })
      setText('')
    },
  })
  return (
    <div>
      {i.notes.length === 0 && <p className="text-[13px] text-muted">No updates yet.</p>}
      <ol className="space-y-4">
        {i.notes.map((n) => {
          const system = n.type === 'SYSTEM'
          return (
            <li key={n.id} className="flex gap-3">
              {system ? (
                <span className="flex size-7 shrink-0 items-center justify-center rounded-full bg-surface-2 text-faint"><Sparkles className="size-3.5" /></span>
              ) : <Avatar name={n.author} size={28} />}
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-baseline gap-x-2 text-xs text-muted">
                  <span className="text-[13px] font-medium text-ink">{system ? 'ResolveIQ' : n.author}</span>
                  <span>{NOTE_LABEL[n.type]}</span>
                  {n.internal && <span className="inline-flex items-center gap-0.5 text-p2"><Lock className="size-3" />Internal</span>}
                  <span className="tabular">{when(n.createdAt)}</span>
                </div>
                <p className={cn('mt-1 whitespace-pre-wrap text-[13.5px] leading-relaxed', system ? 'text-muted' : 'text-ink-2',
                  n.internal && 'rounded-lg bg-p2-soft/60 px-3 py-2')}>{n.content}</p>
              </div>
            </li>
          )
        })}
      </ol>
      {canNote && (
        <form className="mt-5 space-y-2" onSubmit={(e) => { e.preventDefault(); if (text.trim()) add.mutate() }}>
          <Textarea rows={2} value={text} onChange={(e) => setText(e.target.value)} aria-label="Add a note"
            placeholder={staff ? 'Add an investigation note…' : 'Add a comment for the technician…'} />
          <div className="flex items-center justify-between gap-3">
            {canInternal ? (
              <label className="flex cursor-pointer items-center gap-2 text-[13px] text-muted">
                <input type="checkbox" checked={internal} onChange={(e) => setInternal(e.target.checked)} className="accent-[var(--accent)]" />
                Internal (hidden from the reporter)
              </label>
            ) : <span />}
            <Button type="submit" size="sm" variant="primary" loading={add.isPending} disabled={!text.trim()}>Add note</Button>
          </div>
          {add.error && <ErrorNote error={add.error} />}
        </form>
      )}
    </div>
  )
}

function History({ number }: { number: string }) {
  const q = useQuery({ queryKey: ['incident', number, 'audit'], queryFn: () => api.get<AuditEntry[]>(`/incidents/${number}/audit`) })
  if (q.isLoading) return <Skeleton className="h-40" />
  return (
    <ol className="relative ml-1.5 border-l border-line">
      {q.data?.map((a) => (
        <li key={a.id} className="relative pb-4 pl-5 last:pb-0">
          <span className="absolute -left-[5px] top-1.5 size-2.5 rounded-full border-2 border-surface bg-line-strong" />
          <div className="flex flex-wrap items-baseline gap-x-2 text-xs text-muted">
            <span className="text-[13px] font-medium text-ink">{humanise(a.action)}</span>
            {a.previousState && a.newState && (
              <span className="tabular">{humanise(a.previousState)} <span aria-hidden>→</span> {humanise(a.newState)}</span>
            )}
          </div>
          <p className="mt-0.5 text-[13px] text-ink-2">{a.description}</p>
          <p className="mt-0.5 text-xs text-faint">{a.actor}{a.actorRole && a.actorRole !== 'SYSTEM' ? `, ${humanise(a.actorRole).toLowerCase()}` : ''} at {fullDate(a.occurredAt)}</p>
        </li>
      ))}
    </ol>
  )
}

function DetailsCard({ i }: { i: Incident }) {
  const rows: [string, ReactNode][] = [
    ['Assigned to', i.assignee ? <span className="flex items-center gap-1.5"><Avatar name={i.assignee.name} size={18} />{i.assignee.name}</span> : <span className="text-p2">Nobody yet</span>],
    ['Assigned by', i.assignmentStrategy ?? '—'],
    ['Reporter', `${i.reporter.name}`],
    ['Impact', LEVEL_LABEL[i.impact]],
    ['Urgency', LEVEL_LABEL[i.urgency]],
    ['People affected', <span className="tabular">{i.affectedUsers.toLocaleString()}</span>],
    ['Service criticality', LEVEL_LABEL[i.serviceCriticality]],
  ]
  if (i.escalation.level !== 'NONE') rows.push(['Escalated to', `${i.escalation.escalatedTo?.name ?? i.escalation.label}`])
  return (
    <section className="rounded-xl border border-line bg-surface p-4">
      <h2 className="mb-3 text-[13.5px] font-semibold">Details</h2>
      <dl className="space-y-2.5 text-[13px]">
        {rows.map(([k, v]) => (
          <div key={k} className="grid grid-cols-[120px_minmax(0,1fr)] gap-2">
            <dt className="text-muted">{k}</dt>
            <dd className="min-w-0 text-ink">{v}</dd>
          </div>
        ))}
      </dl>
      {i.priorityRationale && (
        <details className="mt-4 border-t border-line pt-3 text-xs text-muted">
          <summary className="cursor-pointer select-none text-[13px] font-medium text-ink-2 hover:text-ink">Why {i.priority}?</summary>
          <ul className="mt-2 space-y-1">{i.priorityRationale.split('; ').map((f) => <li key={f}>{f}</li>)}</ul>
          <p className="mt-2">Calculated by {i.priorityStrategy}</p>
        </details>
      )}
    </section>
  )
}

function RiskCard({ number }: { number: string }) {
  const q = useQuery({ queryKey: ['incident', number, 'risk'], queryFn: () => api.get<SlaRisk>(`/incidents/${number}/sla-risk`) })
  if (!q.data || q.data.level === 'NONE') return null
  const r = q.data
  return (
    <section className="rounded-xl border border-line bg-surface p-4">
      <h2 className="mb-2 flex items-center justify-between text-[13.5px] font-semibold">
        Predicted SLA risk <RiskTag risk={r} />
      </h2>
      <div className="flex items-baseline gap-2">
        <span className="text-2xl font-semibold tabular">{Math.round(r.score * 100)}</span>
        <span className="text-xs text-muted">out of 100</span>
      </div>
      {r.expectedMinutesToResolve !== undefined && r.minutesRemaining !== undefined && (
        <p className="mt-1 text-xs text-muted">About {duration(r.expectedMinutesToResolve)} of work expected, {remaining(r.minutesRemaining)}.</p>
      )}
      <ul className="mt-2 space-y-1 text-xs text-muted">{r.factors.map((f) => <li key={f}>{f}</li>)}</ul>
    </section>
  )
}

function SuggestionsCard({ number, canResolve, onUse }: { number: string; canResolve: boolean; onUse: (r: SuggestedResolution) => void }) {
  const q = useQuery({ queryKey: ['incident', number, 'suggestions'], queryFn: () => api.get<SuggestedResolution[]>(`/incidents/${number}/suggested-resolutions`), staleTime: 120_000 })
  if (!q.data?.length) return null
  return (
    <section className="rounded-xl border border-line bg-surface p-4">
      <h2 className="mb-1 flex items-center gap-2 text-[13.5px] font-semibold"><Lightbulb className="size-4 text-accent-text" />Fixes that worked before</h2>
      <p className="mb-3 text-xs text-muted">From resolved incidents with similar descriptions.</p>
      <ul className="space-y-3">
        {q.data.map((r) => (
          <li key={r.incidentNumber} className="rounded-lg bg-surface-2 p-3">
            <div className="flex items-center justify-between gap-2 text-xs">
              <Link to={`/incidents/${r.incidentNumber}`} className="font-medium tabular text-accent-text hover:underline">{r.incidentNumber}</Link>
              <span className="tabular text-muted">{Math.round(r.relevance * 100)}% match</span>
            </div>
            <p className="mt-1 text-[13px] font-medium">{r.title}</p>
            <p className="mt-1 text-[13px] text-ink-2">{r.summary}</p>
            {canResolve && (
              <button onClick={() => onUse(r)} className="mt-2 inline-flex items-center gap-1 text-xs font-medium text-accent-text hover:underline cursor-pointer">
                <BookOpenCheck className="size-3.5" />Use this fix
              </button>
            )}
          </li>
        ))}
      </ul>
    </section>
  )
}

function SimilarCard({ number }: { number: string }) {
  const q = useQuery({ queryKey: ['incident', number, 'similar'], queryFn: () => api.get<SimilarIncident[]>(`/incidents/${number}/similar`), staleTime: 60_000 })
  const items = (q.data ?? []).filter((s) => s.score >= 0.35)
  if (!items.length) return null
  return (
    <section className="rounded-xl border border-line bg-surface p-4">
      <h2 className="mb-3 text-[13.5px] font-semibold">Similar open incidents</h2>
      <ul className="space-y-2">
        {items.slice(0, 4).map((s) => (
          <li key={s.number}>
            <Link to={`/incidents/${s.number}`} className="block rounded-lg px-2 py-1.5 -mx-2 hover:bg-surface-2">
              <span className="flex justify-between text-xs"><span className="font-medium tabular text-accent-text">{s.number}</span><span className="tabular text-muted">{Math.round(s.score * 100)}%</span></span>
              <span className="block truncate text-[13px]">{s.title}</span>
            </Link>
          </li>
        ))}
      </ul>
    </section>
  )
}

// ---------------------------------------------------------------------------------------------
// Dialogs
// ---------------------------------------------------------------------------------------------

function TextDialog({ open, onClose, title, description, label, submit, onSubmit, busy, optional }: {
  open: boolean; onClose: () => void; title: string; description?: string; label: string; submit: string
  onSubmit: (text: string) => void; busy: boolean; optional?: boolean
}) {
  const [text, setText] = useState('')
  return (
    <Dialog open={open} onOpenChange={(o) => { if (!o) { onClose(); setText('') } }} title={title} description={description}
      footer={<><Button variant="ghost" onClick={onClose}>Cancel</Button><Button variant="primary" loading={busy} disabled={!optional && !text.trim()} onClick={() => onSubmit(text.trim())}>{submit}</Button></>}>
      <Field label={label}><Textarea rows={4} autoFocus value={text} onChange={(e) => setText(e.target.value)} /></Field>
    </Dialog>
  )
}

function ResolveDialog({ open, onClose, onSubmit, busy, prefill }: {
  open: boolean; onClose: () => void; onSubmit: (summary: string, rootCause?: string) => void; busy: boolean
  prefill: { summary?: string; rootCause?: string }
}) {
  const [summary, setSummary] = useState('')
  const [rootCause, setRootCause] = useState('')
  const key = `${prefill.summary ?? ''}|${open}`
  const [lastKey, setLastKey] = useState('')
  if (open && key !== lastKey) {
    setLastKey(key)
    setSummary(prefill.summary ?? '')
    setRootCause(prefill.rootCause ?? '')
  }
  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()} title="Resolve incident" description="The reporter will be asked to confirm the fix. This becomes part of the knowledge base."
      footer={<><Button variant="ghost" onClick={onClose}>Cancel</Button><Button variant="primary" loading={busy} disabled={!summary.trim()} onClick={() => onSubmit(summary.trim(), rootCause.trim() || undefined)}>Resolve</Button></>}>
      <div className="space-y-4">
        <Field label="What did you do?"><Textarea rows={4} autoFocus value={summary} onChange={(e) => setSummary(e.target.value)} /></Field>
        <Field label="Root cause" hint="Optional, but it helps future technicians"><Input value={rootCause} onChange={(e) => setRootCause(e.target.value)} /></Field>
      </div>
    </Dialog>
  )
}

function ReprioritiseDialog({ open, current, onClose, onSubmit, busy }: {
  open: boolean; current?: Priority; onClose: () => void; onSubmit: (p: Priority | undefined, reason: string) => void; busy: boolean
}) {
  const [priority, setPriority] = useState<string>('')
  const [reason, setReason] = useState('')
  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()} title="Change priority"
      description={`Currently ${current ?? 'unset'}. Overrides need a reason and are recorded in the audit trail.`}
      footer={<><Button variant="ghost" onClick={onClose}>Cancel</Button><Button variant="primary" loading={busy} disabled={!!priority && !reason.trim()} onClick={() => onSubmit((priority || undefined) as Priority | undefined, reason.trim())}>{priority ? 'Override priority' : 'Recalculate'}</Button></>}>
      <div className="space-y-4">
        <Field label="New priority">
          <Select value={priority} onChange={(e) => setPriority(e.target.value)}>
            <option value="">Recalculate from impact and urgency</option>
            <option value="P1">P1 Critical</option><option value="P2">P2 High</option><option value="P3">P3 Medium</option><option value="P4">P4 Low</option>
          </Select>
        </Field>
        <Field label="Reason"><Textarea rows={3} value={reason} onChange={(e) => setReason(e.target.value)} /></Field>
      </div>
    </Dialog>
  )
}

function DuplicateDialog({ open, initial, onClose, onSubmit, busy }: {
  open: boolean; initial: string; onClose: () => void; onSubmit: (n: string) => void; busy: boolean
}) {
  const [value, setValue] = useState(initial)
  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()} title="Close as a duplicate"
      description="This incident will be closed and its reporter told to follow the original."
      footer={<><Button variant="ghost" onClick={onClose}>Cancel</Button><Button variant="primary" loading={busy} disabled={!value.trim()} onClick={() => onSubmit(value.trim().toUpperCase())}>Merge</Button></>}>
      <Field label="Original incident number"><Input autoFocus value={value} onChange={(e) => setValue(e.target.value)} placeholder="INC-1037" className="tabular" /></Field>
    </Dialog>
  )
}

function DetailSkeleton() {
  return (
    <div className="space-y-4">
      <Skeleton className="h-4 w-40" />
      <Skeleton className="h-8 w-2/3" />
      <Skeleton className="h-5 w-1/2" />
      <div className="grid gap-6 pt-4 lg:grid-cols-[1fr_320px]">
        <div className="space-y-4"><Skeleton className="h-28" /><Skeleton className="h-64" /></div>
        <Skeleton className="h-72" />
      </div>
    </div>
  )
}

