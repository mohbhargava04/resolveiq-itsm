import { useMemo } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Coffee, ListChecks } from 'lucide-react'
import { api } from '@/lib/api'
import { useUser } from '@/lib/auth'
import { cn } from '@/lib/format'
import type { Dashboard, SlaRisk } from '@/lib/types'
import { EmptyState, PageHeader, Panel, Skeleton } from '@/components/ui'
import { IncidentList, MiniStat, StatStrip } from '@/components/incident-bits'

export default function TechnicianQueue() {
  const user = useUser()
  const { data, isLoading } = useQuery({ queryKey: ['dashboard'], queryFn: () => api.get<Dashboard>('/dashboard') })

  const queue = data?.myQueue ?? []
  const risk = useMemo(() => Object.fromEntries(queue.map((q) => [q.incident.number, q.risk])) as Record<string, SlaRisk>, [queue])
  const atRisk = queue.filter((q) => ['HIGH', 'BREACHED'].includes(q.risk.level)).length
  const load = data?.activeIncidents ?? 0
  const cap = data?.capacity ?? user.technician?.capacity ?? 5

  return (
    <div>
      <PageHeader
        title="My queue"
        description="Sorted by resolution deadline. The bar under each incident shows how much of its SLA has been used."
      />

      <StatStrip className="mb-6 grid-cols-2 md:grid-cols-4">
        <MiniStat label="Active incidents" value={isLoading ? '–' : load} sub={
          <span className="flex items-center gap-2">
            <span className="h-1 w-16 overflow-hidden rounded-full bg-surface-3">
              <span className={cn('block h-full rounded-full', load >= cap ? 'bg-p1' : 'bg-accent')} style={{ width: `${Math.min(100, (load / cap) * 100)}%` }} />
            </span>
            {load} of {cap} capacity
          </span>
        } />
        <MiniStat label="To acknowledge" value={isLoading ? '–' : data?.toAcknowledge ?? 0} tone={(data?.toAcknowledge ?? 0) > 0 ? 'p2' : undefined} sub="Response SLA is running" />
        <MiniStat label="At risk or breached" value={isLoading ? '–' : atRisk} tone={atRisk ? 'p1' : undefined} sub="Predicted from similar incidents" />
        <MiniStat label="Status" value={<span className="text-lg">{data?.available === false ? 'Off duty' : 'On duty'}</span>} sub="Toggle in the sidebar" />
      </StatStrip>

      <Panel title="Assigned to me" action={<Link to="/incidents?mine=1" className="text-[13px] font-medium text-accent-text hover:underline">Include resolved</Link>}>
        {isLoading ? (
          <div className="space-y-2">{[0, 1, 2].map((i) => <Skeleton key={i} className="h-12" />)}</div>
        ) : (
          <IncidentList
            items={queue.map((q) => q.incident)}
            riskByNumber={risk}
            showReporter
            showAssignee={false}
            empty={
              <EmptyState icon={data?.available === false ? <Coffee className="size-8" /> : <ListChecks className="size-8" />} title="Your queue is clear">
                {data?.available === false
                  ? "You're off duty, so no new work will be assigned to you."
                  : 'New incidents matching your skills will appear here as soon as they are assigned.'}
              </EmptyState>
            }
          />
        )}
      </Panel>
    </div>
  )
}
