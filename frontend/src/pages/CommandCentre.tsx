import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { GitBranch, ShieldCheck, Siren, UserPlus } from 'lucide-react'
import { api } from '@/lib/api'
import { ago, cn, duration, humanise, pct, when } from '@/lib/format'
import type { CorrelationCluster, Dashboard, MajorBanner, SlaRisk } from '@/lib/types'
import { Button, Dialog, EmptyState, ErrorNote, Field, PageHeader, Panel, Skeleton, Textarea } from '@/components/ui'
import { IncidentList, MiniStat, PriorityBadge, StatStrip, priorityColor } from '@/components/incident-bits'
import { AssignDialog } from '@/components/AssignDialog'

export default function CommandCentre() {
  const { data, isLoading, dataUpdatedAt } = useQuery({
    queryKey: ['dashboard'],
    queryFn: () => api.get<Dashboard>('/dashboard'),
    refetchInterval: 30_000,
  })
  const [assigning, setAssigning] = useState<string | null>(null)
  const o = data?.overview
  const atRisk = data?.atRisk ?? []
  const risk = useMemo(() => Object.fromEntries(atRisk.map((q) => [q.incident.number, q.risk])) as Record<string, SlaRisk>, [atRisk])

  return (
    <div className="space-y-6">
      <PageHeader
        title="Command centre"
        description={
          <span className="inline-flex items-center gap-2">
            <span className="relative flex size-2">
              <span className="absolute inline-flex size-full animate-ping rounded-full bg-ok opacity-60" />
              <span className="relative inline-flex size-2 rounded-full bg-ok" />
            </span>
            Live view of the service desk{dataUpdatedAt ? `, updated ${when(new Date(dataUpdatedAt).toISOString())}` : ''}
          </span>
        }
        actions={<Link to="/incidents?openOnly=1"><Button>Open incidents</Button></Link>}
      />

      <StatStrip className="grid-cols-2 md:grid-cols-4 xl:grid-cols-7">
        <MiniStat label="Open incidents" value={o?.openIncidents ?? '–'} sub={o ? `${o.criticalOpen} critical` : undefined} tone={o?.criticalOpen ? undefined : undefined} />
        <MiniStat label="SLA compliance" value={o ? pct(o.slaCompliancePercent) : '–'} sub={o ? `${o.slaBreaches} breaches overall` : undefined}
          tone={o && o.slaCompliancePercent < 80 ? 'p2' : undefined} />
        <MiniStat label="Time to acknowledge" value={o?.mttaMinutes !== undefined ? duration(o.mttaMinutes) : '–'} sub="Mean (MTTA)" />
        <MiniStat label="Time to resolve" value={o?.mttrMinutes !== undefined ? duration(o.mttrMinutes) : '–'} sub="Mean (MTTR)" />
        <MiniStat label="Satisfaction" value={o?.averageCsat !== undefined ? `${o.averageCsat.toFixed(1)}` : '–'} sub="Average rating out of 5" />
        <MiniStat label="Escalated" value={o?.escalatedOpen ?? '–'} sub={o ? `${o.unassignedOpen} unassigned` : undefined} tone={o?.escalatedOpen ? 'p1' : undefined} />
        <MiniStat label="Solved by self-help" value={data?.knowledge?.deflections ?? '–'} sub="Incidents avoided via the knowledge base" tone="ok" />
      </StatStrip>

      {data?.majorIncidents?.map((m) => <MajorCard key={m.number} m={m} />)}

      {data?.correlations?.map((c) => <CorrelationAlert key={c.rootServiceCode} cluster={c} />)}

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1.65fr)_minmax(0,1fr)]">
        <Panel title="Needs attention" description="Predicted SLA risk, highest first">
          {isLoading ? <Skeleton className="h-48" /> : (
            <IncidentList
              items={atRisk.map((q) => q.incident)}
              riskByNumber={risk}
              empty={<EmptyState icon={<ShieldCheck className="size-8" />} title="Nothing is at risk">Every open incident is comfortably inside its SLA.</EmptyState>}
            />
          )}
        </Panel>

        <div className="space-y-6">
          <Panel title="Waiting for an owner" description={data?.unassigned?.length ? 'Automatic assignment found nobody eligible' : undefined}>
            {isLoading ? <Skeleton className="h-24" /> : data?.unassigned?.length ? (
              <ul className="divide-y divide-line">
                {data.unassigned.map((i) => (
                  <li key={i.number} className="flex items-center gap-3 py-2.5">
                    <PriorityBadge priority={i.priority} />
                    <Link to={`/incidents/${i.number}`} className="min-w-0 flex-1">
                      <span className="block truncate text-[13.5px] font-medium hover:text-accent-text">{i.title}</span>
                      <span className="flex gap-2 text-xs text-muted"><span className="tabular">{i.number}</span><span>reported {ago(i.createdAt)}</span></span>
                    </Link>
                    <Button size="sm" variant="primary" icon={<UserPlus className="size-3.5" />} onClick={() => setAssigning(i.number)}>Assign</Button>
                  </li>
                ))}
              </ul>
            ) : <p className="py-4 text-[13px] text-muted">Every open incident has an owner.</p>}
          </Panel>

          <Panel title="Open by priority">
            {isLoading ? <Skeleton className="h-28" /> : <PriorityMix rows={data?.byPriority ?? []} />}
          </Panel>
        </div>
      </div>

      <Panel title="Recent activity" description="From the audit trail">
        {isLoading ? <Skeleton className="h-40" /> : (
          <ol className="relative space-y-0">
            {data?.recentActivity?.slice(0, 10).map((a) => (
              <li key={a.id} className="grid grid-cols-[64px_minmax(0,1fr)] gap-3 py-2">
                <span className="pt-0.5 text-xs tabular text-faint">{when(a.occurredAt)}</span>
                <span className="min-w-0 text-[13px]">
                  <span className="font-medium">{a.actor}</span>{' '}
                  <span className="text-muted">{humanise(a.action).toLowerCase()}</span>{' '}
                  {a.incidentNumber && <Link to={`/incidents/${a.incidentNumber}`} className="font-medium text-accent-text tabular hover:underline">{a.incidentNumber}</Link>}
                  <span className="mt-0.5 block truncate text-xs text-muted">{a.description}</span>
                </span>
              </li>
            ))}
          </ol>
        )}
      </Panel>

      {assigning && <AssignDialog number={assigning} open onOpenChange={(o) => !o && setAssigning(null)} />}
    </div>
  )
}

function MajorCard({ m }: { m: MajorBanner }) {
  return (
    <section className="flex flex-wrap items-center gap-4 rounded-xl border border-p1/35 bg-p1-soft/50 px-5 py-4">
      <span className="flex size-8 items-center justify-center rounded-lg bg-p1 text-p1-ink"><Siren className="size-4" /></span>
      <div className="min-w-0 flex-1">
        <p className="text-xs font-semibold uppercase tracking-wide text-p1">Major incident · {m.stageLabel}</p>
        <Link to={`/incidents/${m.number}`} className="mt-0.5 block text-[15px] font-semibold hover:text-accent-text">{m.title}</Link>
        <p className="mt-0.5 truncate text-[13px] text-muted">{m.latestUpdate?.message}</p>
      </div>
      <div className="text-right text-xs text-muted">
        <div className="text-xl font-semibold tabular text-ink">{m.linkedCount}</div>
        linked reports · declared {ago(m.declaredAt)}
      </div>
    </section>
  )
}

/** A correlated outage: several services failing that share one upstream dependency. */
function CorrelationAlert({ cluster }: { cluster: CorrelationCluster }) {
  const dependents = cluster.affectedServices.filter((s) => s !== cluster.rootServiceName)
  const [declaring, setDeclaring] = useState(false)
  return (
    <section className="overflow-hidden rounded-xl border border-p2/40 bg-surface">
      <div className="flex flex-wrap items-start gap-4 border-l-4 border-p2 px-5 py-4">
        <span className="mt-0.5 flex size-8 items-center justify-center rounded-lg bg-p2-soft text-p2"><GitBranch className="size-4" /></span>
        <div className="min-w-0 flex-1">
          <h2 className="text-[15px] font-semibold">Probable common cause: {cluster.rootServiceName}</h2>
          <p className="mt-0.5 max-w-3xl text-[13px] text-muted">
            {cluster.incidentNumbers.length} incidents in the last hour come from services that depend on {cluster.rootServiceName}.
            Fixing it is likely to resolve them together.
          </p>
          <div className="mt-3 flex flex-wrap items-center gap-2 text-[13px]">
            {dependents.map((s) => (
              <span key={s} className="rounded-md border border-line bg-surface-2 px-2 py-0.5 text-ink-2">{s}</span>
            ))}
            <span className="text-faint">depend on</span>
            <span className="rounded-md bg-p2-soft px-2 py-0.5 font-medium text-p2">{cluster.rootServiceName}</span>
          </div>
          <div className="mt-3 flex flex-wrap gap-x-3 gap-y-1 text-xs">
            {cluster.incidentNumbers.map((n) => (
              <Link key={n} to={`/incidents/${n}`} className="font-medium tabular text-accent-text hover:underline">{n}</Link>
            ))}
          </div>
        </div>
        <div className="flex flex-col items-end gap-3 text-right">
          <div>
            <div className="text-xl font-semibold tabular">{Math.round(cluster.confidence * 100)}%</div>
            <div className="text-xs text-muted">confidence</div>
          </div>
          {cluster.majorNumber ? (
            <Link to={`/incidents/${cluster.majorNumber}`} className="text-xs font-medium text-p1 hover:underline">Handled as major incident {cluster.majorNumber}</Link>
          ) : cluster.suggestedMajor && (
            <Button size="sm" variant="danger" icon={<Siren className="size-3.5" />} onClick={() => setDeclaring(true)}>Declare major incident</Button>
          )}
        </div>
      </div>
      {declaring && <DeclareFromCluster cluster={cluster} onClose={() => setDeclaring(false)} />}
    </section>
  )
}

/** One click turns a detected outage into a coordinated major incident with every report linked. */
function DeclareFromCluster({ cluster, onClose }: { cluster: CorrelationCluster; onClose: () => void }) {
  const qc = useQueryClient()
  const dependents = cluster.affectedServices.filter((s) => s !== cluster.rootServiceName)
  const [summary, setSummary] = useState(
    `${dependents.join(', ')} ${dependents.length === 1 ? 'is' : 'are'} failing. The likely cause is ${cluster.rootServiceName}; we are investigating and will post updates here.`)
  const declare = useMutation({
    mutationFn: () => api.post(`/incidents/${cluster.suggestedMajor}/major`, { summary, link: cluster.incidentNumbers }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['dashboard'] })
      qc.invalidateQueries({ queryKey: ['major-incidents'] })
      toast.success(`Major incident declared on ${cluster.suggestedMajor}`, { description: `${cluster.incidentNumbers.length - 1} reports linked; everyone affected has been alerted.` })
      onClose()
    },
  })
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()} title="Declare a major incident" width={540}
      description={`${cluster.suggestedMajor} becomes the coordinating incident and the other ${cluster.incidentNumbers.length - 1} reports are linked to it. Their reporters get every update, and resolving it resolves them all.`}
      footer={<><Button variant="ghost" onClick={onClose}>Cancel</Button><Button variant="danger" loading={declare.isPending} disabled={!summary.trim()} onClick={() => declare.mutate()}>Declare and link {cluster.incidentNumbers.length - 1}</Button></>}>
      <Field label="First public update" htmlFor="cl-summary" hint="Shown in the banner every user sees">
        <Textarea id="cl-summary" rows={4} value={summary} onChange={(e) => setSummary(e.target.value)} />
      </Field>
      {declare.error ? <div className="mt-3"><ErrorNote error={declare.error} /></div> : null}
    </Dialog>
  )
}

function PriorityMix({ rows }: { rows: { key: string; label: string; total: number; open: number }[] }) {
  const open = rows.filter((r) => r.key !== 'NONE')
  const max = Math.max(1, ...open.map((r) => r.open))
  return (
    <ul className="space-y-2.5">
      {(['P1', 'P2', 'P3', 'P4'] as const).map((p) => {
        const r = open.find((x) => x.key === p)
        const n = r?.open ?? 0
        return (
          <li key={p} className="grid grid-cols-[34px_1fr_28px] items-center gap-3">
            <PriorityBadge priority={p} className="w-8 justify-center" />
            <span className="h-2 overflow-hidden rounded-full bg-surface-2">
              <span className={cn('block h-full rounded-full transition-all')} style={{ width: `${(n / max) * 100}%`, background: priorityColor(p) }} />
            </span>
            <span className="text-right text-[13px] font-medium tabular">{n}</span>
          </li>
        )
      })}
    </ul>
  )
}
