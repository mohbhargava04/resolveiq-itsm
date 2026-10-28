import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { format } from 'date-fns'
import { Bar, BarChart, CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { Download } from 'lucide-react'
import { api, downloadCsv } from '@/lib/api'
import { cn, duration, pct, ROLE_LABEL } from '@/lib/format'
import type { CountRow, DayRow, Overview, SlaRow, TechnicianLoad } from '@/lib/types'
import { Avatar, Button, PageHeader, Panel, Segmented, Skeleton } from '@/components/ui'
import { MiniStat, PriorityBadge, StatStrip } from '@/components/incident-bits'

const tooltipStyle = {
  contentStyle: {
    background: 'var(--surface)', border: '1px solid var(--line)', borderRadius: 8, fontSize: 12,
    boxShadow: 'var(--shadow-pop)', color: 'var(--ink)',
  },
  labelStyle: { color: 'var(--muted)', marginBottom: 4 },
  itemStyle: { color: 'var(--ink)', padding: 0 },
}

export default function Reports() {
  const [days, setDays] = useState<'14' | '30'>('14')
  const overview = useQuery({ queryKey: ['reports', 'overview'], queryFn: () => api.get<Overview>('/reports/overview') })
  const trend = useQuery({ queryKey: ['reports', 'trend', days], queryFn: () => api.get<DayRow[]>(`/reports/trend?days=${days}`) })
  const sla = useQuery({ queryKey: ['reports', 'sla'], queryFn: () => api.get<SlaRow[]>('/reports/sla') })
  const workload = useQuery({ queryKey: ['reports', 'workload'], queryFn: () => api.get<TechnicianLoad[]>('/reports/technician-workload') })
  const byService = useQuery({ queryKey: ['reports', 'service'], queryFn: () => api.get<CountRow[]>('/reports/by-service') })
  const byCategory = useQuery({ queryKey: ['reports', 'category'], queryFn: () => api.get<CountRow[]>('/reports/by-category') })
  const o = overview.data

  return (
    <div className="space-y-6">
      <PageHeader
        title="Reports"
        description="How the service desk is performing across all incidents on record."
        actions={<Button icon={<Download className="size-4" />} onClick={() => downloadCsv('/reports/export.csv', 'incidents.csv')}>Export CSV</Button>}
      />

      <StatStrip className="grid-cols-2 md:grid-cols-4">
        <MiniStat label="Incidents on record" value={o?.totalIncidents ?? '–'} sub={o ? `${o.resolvedIncidents} resolved, ${o.openIncidents} open` : undefined} />
        <MiniStat label="SLA compliance" value={o ? pct(o.slaCompliancePercent) : '–'} sub={o ? `${o.slaBreaches} incidents breached a target` : undefined} />
        <MiniStat label="Mean time to resolve" value={o?.mttrMinutes !== undefined ? duration(o.mttrMinutes) : '–'} sub={o?.mttaMinutes !== undefined ? `Acknowledged in ${duration(o.mttaMinutes)} on average` : undefined} />
        <MiniStat label="Customer satisfaction" value={o?.averageCsat !== undefined ? `${o.averageCsat.toFixed(1)} / 5` : '–'} sub={o ? `${o.duplicatesDetected} ${o.duplicatesDetected === 1 ? 'duplicate' : 'duplicates'} caught, ${pct(o.autoClassifiedPercent)} categorised by AI` : undefined} />
      </StatStrip>

      <Panel
        title="Incidents reported and resolved"
        description="Per day. A widening gap means the backlog is growing."
        action={<div className="w-40"><Segmented name="Range" value={days} onChange={setDays} options={[{ value: '14', label: '14 days' }, { value: '30', label: '30 days' }]} /></div>}
      >
        {!trend.data ? <Skeleton className="h-64" /> : (
          <div className="h-64">
            <ResponsiveContainer>
              <LineChart data={trend.data} margin={{ top: 8, right: 8, left: -18, bottom: 0 }}>
                <CartesianGrid vertical={false} strokeDasharray="0" />
                <XAxis dataKey="date" tickLine={false} axisLine={false} tickFormatter={(d) => format(new Date(d), 'd MMM')} minTickGap={24} />
                <YAxis allowDecimals={false} tickLine={false} axisLine={false} width={40} />
                <Tooltip {...tooltipStyle} labelFormatter={(d) => format(new Date(d as string), 'EEEE d MMMM')} />
                <Legend iconType="plainline" wrapperStyle={{ fontSize: 12, color: 'var(--muted)' }} />
                <Line type="monotoneX" dataKey="created" name="Reported" stroke="var(--chart-1)" strokeWidth={2} dot={false} activeDot={{ r: 4, strokeWidth: 2, stroke: 'var(--surface)' }} />
                <Line type="monotoneX" dataKey="resolved" name="Resolved" stroke="var(--chart-2)" strokeWidth={2} dot={false} activeDot={{ r: 4, strokeWidth: 2, stroke: 'var(--surface)' }} />
              </LineChart>
            </ResponsiveContainer>
          </div>
        )}
      </Panel>

      <div className="grid gap-6 xl:grid-cols-2">
        <Panel title="SLA performance by priority" description="Targets met versus breached, and average times">
          {!sla.data ? <Skeleton className="h-40" /> : (
            <table className="w-full text-[13px]">
              <thead>
                <tr className="text-left text-xs text-muted">
                  <th className="pb-2 font-medium">Priority</th>
                  <th className="pb-2 font-medium">Compliance</th>
                  <th className="pb-2 text-right font-medium">Breached</th>
                  <th className="pb-2 text-right font-medium">Avg. response</th>
                  <th className="pb-2 text-right font-medium">Avg. resolution</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {sla.data.map((r) => (
                  <tr key={r.priority}>
                    <td className="py-2.5"><PriorityBadge priority={r.priority} showLabel /></td>
                    <td className="py-2.5">
                      <div className="flex items-center gap-2">
                        <span className="h-1.5 w-20 overflow-hidden rounded-full bg-surface-3">
                          <span className={cn('block h-full rounded-full', r.compliancePercent >= 90 ? 'bg-ok' : r.compliancePercent >= 70 ? 'bg-p2' : 'bg-p1')} style={{ width: `${r.compliancePercent}%` }} />
                        </span>
                        <span className="tabular">{r.total ? pct(r.compliancePercent) : '—'}</span>
                      </div>
                    </td>
                    <td className="py-2.5 text-right tabular">{r.breached} of {r.total}</td>
                    <td className="py-2.5 text-right tabular">{duration(r.avgResponseMinutes)}</td>
                    <td className="py-2.5 text-right tabular">{duration(r.avgResolutionMinutes)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </Panel>

        <Panel title="Incidents by service" description="All time, most affected first">
          {!byService.data ? <Skeleton className="h-40" /> : <CountBars rows={byService.data.slice(0, 8)} />}
        </Panel>
      </div>

      <Panel title="Technician workload" description="Current load against capacity, with the last 30 days of output">
        {!workload.data ? <Skeleton className="h-60" /> : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[720px] text-[13px]">
              <thead>
                <tr className="text-left text-xs text-muted">
                  <th className="pb-2 font-medium">Technician</th>
                  <th className="pb-2 font-medium">Current load</th>
                  <th className="pb-2 text-right font-medium">Resolved (30 d)</th>
                  <th className="pb-2 text-right font-medium">Avg. resolution</th>
                  <th className="pb-2 text-right font-medium">Satisfaction</th>
                  <th className="pb-2 text-right font-medium">Breaches</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {workload.data.map((t) => (
                  <tr key={t.id}>
                    <td className="py-2.5">
                      <div className="flex items-center gap-2.5">
                        <Avatar name={t.name} size={28} />
                        <div>
                          <div className="flex items-center gap-1.5 font-medium">
                            {t.name}
                            {!t.available && <span className="rounded bg-surface-2 px-1.5 text-[11px] font-normal text-muted">Off duty</span>}
                          </div>
                          <div className="text-xs text-muted">{ROLE_LABEL[t.role]}, {t.skills.map((s) => s.toLowerCase()).join(', ')}</div>
                        </div>
                      </div>
                    </td>
                    <td className="py-2.5">
                      <div className="flex items-center gap-2">
                        <span className="flex gap-0.5" aria-label={`${t.activeIncidents} of ${t.capacity}`}>
                          {Array.from({ length: t.capacity }).map((_, k) => (
                            <span key={k} className={cn('h-3 w-2.5 rounded-sm', k < t.activeIncidents ? (t.activeIncidents >= t.capacity ? 'bg-p1' : 'bg-accent') : 'bg-surface-3')} />
                          ))}
                        </span>
                        <span className="tabular text-muted">{t.activeIncidents}/{t.capacity}</span>
                      </div>
                    </td>
                    <td className="py-2.5 text-right tabular">{t.resolvedLast30Days}</td>
                    <td className="py-2.5 text-right tabular">{duration(t.avgResolutionMinutes)}</td>
                    <td className="py-2.5 text-right tabular">{t.csat !== undefined ? t.csat.toFixed(1) : '—'}</td>
                    <td className={cn('py-2.5 text-right tabular', t.slaBreaches > 0 && 'text-p1')}>{t.slaBreaches}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Panel>

      <Panel title="Incidents by category">
        {!byCategory.data ? <Skeleton className="h-56" /> : (
          <div className="h-60">
            <ResponsiveContainer>
              <BarChart data={byCategory.data} margin={{ top: 8, right: 8, left: -18, bottom: 0 }} barCategoryGap="30%">
                <CartesianGrid vertical={false} />
                <XAxis dataKey="label" tickLine={false} axisLine={false} interval={0} tick={{ fontSize: 11 }} />
                <YAxis allowDecimals={false} tickLine={false} axisLine={false} width={40} />
                <Tooltip {...tooltipStyle} />
                <Bar dataKey="total" name="Incidents" fill="var(--chart-1)" radius={[4, 4, 0, 0]} maxBarSize={36} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        )}
      </Panel>
    </div>
  )
}

function CountBars({ rows }: { rows: CountRow[] }) {
  const max = Math.max(1, ...rows.map((r) => r.total))
  return (
    <ul className="space-y-2.5">
      {rows.map((r) => (
        <li key={r.key} className="grid grid-cols-[minmax(0,150px)_1fr_auto] items-center gap-3 text-[13px]">
          <span className="truncate text-ink-2" title={r.label}>{r.label}</span>
          <span className="relative h-2 overflow-hidden rounded-full bg-surface-2" title={`${r.total} total, ${r.open} open`}>
            <span className="absolute inset-y-0 left-0 rounded-full bg-chart-1" style={{ width: `${(r.total / max) * 100}%` }} />
          </span>
          <span className="w-24 text-right tabular">
            {r.total}
            {r.open > 0 && <span className="ml-1 text-xs text-p2">{r.open} open</span>}
          </span>
        </li>
      ))}
    </ul>
  )
}
