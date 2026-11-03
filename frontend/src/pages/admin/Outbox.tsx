import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Bell, ChevronDown, Inbox, Mail, MessageSquareText, Send } from 'lucide-react'
import { api, qs } from '@/lib/api'
import { cn, when } from '@/lib/format'
import type { InboundEmail, IncidentSummary, OutboxRow, Page } from '@/lib/types'
import { Button, EmptyState, ErrorNote, Field, Input, PageHeader, Panel, Segmented, Skeleton, Tabs, Textarea } from '@/components/ui'

const ICON = { IN_APP: Bell, EMAIL: Mail, SMS: MessageSquareText }

export default function Outbox() {
  const [tab, setTab] = useState<'out' | 'in'>('out')
  return (
    <div>
      <PageHeader title="E-mail and SMS" description="Every notification sent, and every e-mail received. Without SMTP or Twilio credentials, sending is simulated and logged here." />
      <Tabs label="Direction" value={tab} onChange={setTab} tabs={[{ value: 'out', label: 'Sent' }, { value: 'in', label: 'Received e-mail' }]} />
      <div className="mt-5">{tab === 'out' ? <Outgoing /> : <Incoming />}</div>
    </div>
  )
}

function Outgoing() {
  const [channel, setChannel] = useState<'ALL' | 'EMAIL' | 'SMS' | 'IN_APP'>('ALL')
  const [open, setOpen] = useState<number | null>(null)
  const q = useQuery({
    queryKey: ['outbox', channel],
    queryFn: () => api.get<OutboxRow[]>(`/admin/notifications/outbox?limit=150${channel === 'ALL' ? '' : `&channel=${channel}`}`),
    refetchInterval: 15_000,
  })
  return (
    <>
      <div className="mb-4 w-full max-w-sm">
        <Segmented name="Channel" value={channel} onChange={setChannel}
          options={[{ value: 'ALL', label: 'All' }, { value: 'EMAIL', label: 'Email' }, { value: 'SMS', label: 'SMS' }, { value: 'IN_APP', label: 'In-app' }]} />
      </div>
      <Panel bodyClassName="p-0">
        {q.isLoading ? <Skeleton className="m-5 h-80" /> : (
          <ul className="divide-y divide-line">
            {q.data?.map((n) => {
              const Icon = ICON[n.channel]
              const expanded = open === n.id
              return (
                <li key={n.id}>
                  <button onClick={() => setOpen(expanded ? null : n.id)} aria-expanded={expanded}
                    className="grid w-full grid-cols-[28px_minmax(0,1fr)_auto] items-start gap-3 px-5 py-3 text-left hover:bg-surface-2/50 cursor-pointer">
                    <span className={cn('mt-0.5 flex size-7 items-center justify-center rounded-md', n.urgent ? 'bg-p1-soft text-p1' : 'bg-surface-2 text-muted')}><Icon className="size-3.5" /></span>
                    <span className="min-w-0">
                      <span className="block truncate text-[13.5px] font-medium">{n.subject}</span>
                      <span className="block truncate text-xs text-muted">
                        To {n.recipient}{n.address ? ` (${n.address})` : ''}{n.incidentNumber ? ` · ${n.incidentNumber}` : ''}
                      </span>
                      {n.error && <span className="block text-xs text-p1">{n.error}</span>}
                    </span>
                    <span className="flex items-start gap-2 text-right">
                      <span>
                        <span className={cn('block text-xs font-medium', n.status === 'FAILED' ? 'text-p1' : n.status === 'SENT' ? 'text-ok' : 'text-muted')}>
                          {n.status === 'SIMULATED' ? 'Simulated' : n.status === 'SENT' ? 'Delivered' : 'Failed'}
                        </span>
                        <span className="block text-xs tabular text-faint">{when(n.createdAt)}</span>
                      </span>
                      <ChevronDown className={cn('mt-0.5 size-4 text-faint transition-transform', expanded && 'rotate-180')} />
                    </span>
                  </button>
                  {expanded && (
                    <div className="border-t border-line bg-bg/60 px-5 py-3 pl-[60px]">
                      <pre className="whitespace-pre-wrap break-words font-sans text-[13px] text-ink-2">{n.message}</pre>
                      {n.incidentNumber && <Link to={`/incidents/${n.incidentNumber}`} className="mt-2 inline-block text-xs font-medium text-accent-text hover:underline">Open {n.incidentNumber}</Link>}
                    </div>
                  )}
                </li>
              )
            })}
          </ul>
        )}
      </Panel>
    </>
  )
}

const OUTCOME: Record<string, { label: string; tone: string }> = {
  ANSWERED_QUESTION: { label: 'Answered a question', tone: 'text-ok' },
  COMMENT_ADDED: { label: 'Added to conversation', tone: 'text-ok' },
  INCIDENT_CREATED: { label: 'New incident', tone: 'text-accent-text' },
  REJECTED: { label: 'Rejected', tone: 'text-p1' },
  IGNORED: { label: 'Ignored', tone: 'text-muted' },
}

function Incoming() {
  const qc = useQueryClient()
  const log = useQuery({ queryKey: ['inbound'], queryFn: () => api.get<{ address: string; enabled: boolean; emails: InboundEmail[] }>('/admin/inbound'), refetchInterval: 15_000 })
  const waiting = useQuery({ queryKey: ['incidents', 'awaiting'], queryFn: () => api.get<Page<IncidentSummary>>(`/incidents${qs({ status: 'AWAITING_INFO', size: 1 })}`) })
  const [mail, setMail] = useState({ from: '', subject: '', text: '' })
  const simulate = useMutation({
    mutationFn: () => api.post<{ outcome: string; detail: string; incidentNumber?: string }>('/admin/inbound/simulate', mail),
    onSuccess: (r) => {
      qc.invalidateQueries({ queryKey: ['inbound'] })
      qc.invalidateQueries({ queryKey: ['incidents'] })
      ;(r.outcome === 'REJECTED' ? toast.error : toast.success)(OUTCOME[r.outcome]?.label ?? r.outcome, { description: `${r.detail}${r.incidentNumber ? ` (${r.incidentNumber})` : ''}` })
    },
  })
  const w = waiting.data?.items[0]
  const presets = [
    w && { label: `Reporter answers ${w.number} by e-mail`, mail: { from: `${w.reporter.name} <${w.reporter.name.split(' ')[0].toLowerCase()}@resolveiq.test>`, subject: `RE: Action needed on ${w.number} [${w.number}]`, text: 'Here is the information you asked for: it happens every time, on both Wi-Fi and cable.\n\nOn Tue, ResolveIQ wrote:\n> Which model is it?' } },
    { label: 'New problem e-mailed to support', mail: { from: 'rohan@resolveiq.test', subject: 'Campus Wi-Fi not working in hostel block D', text: 'No wireless connection at all since 9 pm. Several of us are affected.' } },
    { label: 'E-mail from outside the company', mail: { from: 'promo@unknown-shop.example', subject: 'You won a prize', text: 'Click here' } },
  ].filter(Boolean) as { label: string; mail: typeof mail }[]

  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_380px]">
      <Panel title="Received" description={log.data ? `Support mailbox: ${log.data.address}. ${log.data.enabled ? 'Provider webhook is on.' : 'Provider webhook is off (no secret set); use the simulator.'}` : undefined} bodyClassName="p-0">
        {log.isLoading ? <Skeleton className="m-5 h-64" /> : !log.data?.emails.length ? (
          <EmptyState icon={<Inbox className="size-8" />} title="No e-mail received yet">Replies to notification e-mails appear here.</EmptyState>
        ) : (
          <ul className="divide-y divide-line">
            {log.data.emails.map((e) => (
              <li key={e.id} className="px-5 py-3">
                <div className="flex items-baseline justify-between gap-3">
                  <span className="min-w-0 truncate text-[13.5px] font-medium">{e.subject}</span>
                  <span className={cn('shrink-0 text-xs font-medium', OUTCOME[e.outcome]?.tone)}>{OUTCOME[e.outcome]?.label ?? e.outcome}</span>
                </div>
                <p className="mt-0.5 text-xs text-muted">
                  From {e.from} · {when(e.receivedAt)}
                  {e.incidentNumber && <> · <Link to={`/incidents/${e.incidentNumber}`} className="tabular text-accent-text hover:underline">{e.incidentNumber}</Link></>}
                </p>
                {e.excerpt && <p className="mt-1 line-clamp-2 text-[13px] text-ink-2">{e.excerpt}</p>}
                {e.detail && <p className="mt-1 text-xs text-faint">{e.detail}</p>}
              </li>
            ))}
          </ul>
        )}
      </Panel>

      <Panel title="Simulate an incoming e-mail" description="Processes it exactly as if it had arrived from the mail provider.">
        <div className="mb-4 flex flex-wrap gap-1.5">
          {presets.map((p) => (
            <button key={p.label} type="button" onClick={() => setMail(p.mail)}
              className="rounded-md border border-line px-2 py-1 text-xs text-ink-2 hover:bg-surface-2 cursor-pointer">{p.label}</button>
          ))}
        </div>
        <form onSubmit={(e) => { e.preventDefault(); simulate.mutate() }} className="space-y-3">
          <Field label="From" htmlFor="in-from"><Input id="in-from" value={mail.from} onChange={(e) => setMail({ ...mail, from: e.target.value })} placeholder="ava@resolveiq.test" required /></Field>
          <Field label="Subject" htmlFor="in-subj" hint="Keep [INC-1234] in a reply's subject"><Input id="in-subj" value={mail.subject} onChange={(e) => setMail({ ...mail, subject: e.target.value })} /></Field>
          <Field label="Body" htmlFor="in-body"><Textarea id="in-body" rows={5} value={mail.text} onChange={(e) => setMail({ ...mail, text: e.target.value })} /></Field>
          {simulate.error ? <ErrorNote error={simulate.error} /> : null}
          <div className="flex justify-end"><Button type="submit" variant="primary" icon={<Send className="size-3.5" />} loading={simulate.isPending}>Deliver</Button></div>
        </form>
      </Panel>
    </div>
  )
}
