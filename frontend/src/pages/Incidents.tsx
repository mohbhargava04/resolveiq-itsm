import { useMemo } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ChevronLeft, ChevronRight, Download, Inbox, Plus, Search, X } from 'lucide-react'
import { api, downloadCsv, qs } from '@/lib/api'
import { useAuth, useUser } from '@/lib/auth'
import { cn, SLA_LABEL, STATUS_LABEL } from '@/lib/format'
import type { CategoryView, IncidentStatus, IncidentSummary, Page, Priority, ServiceView, SlaStatus, User } from '@/lib/types'
import { Button, EmptyState, Input, PageHeader, Panel, Select, Skeleton } from '@/components/ui'
import { IncidentList, PriorityBadge } from '@/components/incident-bits'

const STATUSES: IncidentStatus[] = ['NEW', 'CLASSIFIED', 'ASSIGNED', 'ACKNOWLEDGED', 'IN_PROGRESS', 'AWAITING_INFO', 'RESOLVED', 'VERIFIED', 'CLOSED']
const SLA: SlaStatus[] = ['ON_TRACK', 'AT_RISK', 'PAUSED', 'BREACHED', 'MET']
const PRIORITIES: Priority[] = ['P1', 'P2', 'P3', 'P4']

export default function Incidents() {
  const user = useUser()
  const { is } = useAuth()
  const staff = user.role !== 'EMPLOYEE'
  const [params, setParams] = useSearchParams()

  const get = (k: string) => params.get(k) ?? ''
  const priorities = params.getAll('priority') as Priority[]
  const set = (k: string, v: string | null) => {
    const next = new URLSearchParams(params)
    if (v) next.set(k, v); else next.delete(k)
    if (k !== 'page') next.delete('page')
    setParams(next, { replace: true })
  }
  const togglePriority = (p: Priority) => {
    const next = new URLSearchParams(params)
    const all = next.getAll('priority')
    next.delete('priority')
    ;(all.includes(p) ? all.filter((x) => x !== p) : [...all, p]).forEach((x) => next.append('priority', x))
    next.delete('page')
    setParams(next, { replace: true })
  }

  const status = get('status')
  const query = {
    q: get('q'),
    status: status === 'OPEN' ? undefined : status,
    openOnly: status === 'OPEN' || get('openOnly') === '1',
    priority: priorities,
    service: get('service'),
    category: get('category'),
    slaStatus: get('sla'),
    assigneeId: get('assignee'),
    mine: get('mine') === '1',
    escalated: get('escalated') === '1',
    from: get('from'),
    to: get('to'),
    sort: get('sort') || 'createdAt',
    direction: get('dir') || 'desc',
    page: Number(get('page') || 0),
    size: 20,
  }
  const path = `/incidents${qs(query)}`
  const list = useQuery({ queryKey: ['incidents', path], queryFn: () => api.get<Page<IncidentSummary>>(path), placeholderData: (p) => p })

  const services = useQuery({ queryKey: ['services'], queryFn: () => api.get<ServiceView[]>('/services'), staleTime: 300_000 })
  const categories = useQuery({ queryKey: ['categories'], queryFn: () => api.get<CategoryView[]>('/categories'), staleTime: 300_000 })
  const technicians = useQuery({ queryKey: ['technicians'], queryFn: () => api.get<User[]>('/technicians'), enabled: is('TEAM_LEAD'), staleTime: 60_000 })

  const activeFilters = useMemo(() => [...params.keys()].filter((k) => !['page', 'sort', 'dir'].includes(k)).length, [params])
  const data = list.data

  return (
    <div>
      <PageHeader
        title={staff ? 'Incidents' : 'My incidents'}
        description={staff ? 'Search and filter every incident in the organisation.' : 'Everything you have reported, newest first.'}
        actions={
          <>
            {is('TEAM_LEAD') && (
              <Button icon={<Download className="size-4" />} onClick={() => downloadCsv('/reports/export.csv', 'incidents.csv')}>Export CSV</Button>
            )}
            <Link to="/report"><Button variant="primary" icon={<Plus className="size-4" />}>Report</Button></Link>
          </>
        }
      />

      <div className="mb-4 space-y-3">
        <div className="flex flex-wrap items-center gap-2">
          <div className="relative min-w-[220px] flex-1">
            <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-faint" />
            <Input aria-label="Search" value={get('q')} onChange={(e) => set('q', e.target.value)} placeholder="Search number, title or description" className="pl-9" />
          </div>
          <Select aria-label="Status" value={status} onChange={(e) => set('status', e.target.value)} className="w-auto min-w-36">
            <option value="">Any status</option>
            <option value="OPEN">Still open</option>
            {STATUSES.map((s) => <option key={s} value={s}>{STATUS_LABEL[s]}</option>)}
          </Select>
          <Select aria-label="Service" value={get('service')} onChange={(e) => set('service', e.target.value)} className="w-auto min-w-36">
            <option value="">Any service</option>
            {services.data?.map((s) => <option key={s.code} value={s.code}>{s.name}</option>)}
          </Select>
          <Select aria-label="Category" value={get('category')} onChange={(e) => set('category', e.target.value)} className="w-auto min-w-36">
            <option value="">Any category</option>
            {categories.data?.map((c) => <option key={c.code} value={c.code}>{c.name}</option>)}
          </Select>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <div className="flex items-center gap-1 rounded-lg bg-surface-2 p-1" role="group" aria-label="Priority">
            {PRIORITIES.map((p) => (
              <button key={p} onClick={() => togglePriority(p)} aria-pressed={priorities.includes(p)}
                className={cn('rounded-md px-1 py-0.5 transition-opacity cursor-pointer', priorities.length > 0 && !priorities.includes(p) && 'opacity-35')}>
                <PriorityBadge priority={p} />
              </button>
            ))}
          </div>
          <Select aria-label="SLA" value={get('sla')} onChange={(e) => set('sla', e.target.value)} className="h-8 w-auto text-[13px]">
            <option value="">Any SLA state</option>
            {SLA.map((s) => <option key={s} value={s}>{SLA_LABEL[s]}</option>)}
          </Select>
          {staff && (
            <label className="flex h-8 cursor-pointer items-center gap-2 rounded-lg border border-line bg-surface px-2.5 text-[13px] text-ink-2">
              <input type="checkbox" checked={get('mine') === '1'} onChange={(e) => set('mine', e.target.checked ? '1' : null)} className="accent-[var(--accent)]" />
              Assigned to me
            </label>
          )}
          {is('TEAM_LEAD') && (
            <Select aria-label="Technician" value={get('assignee')} onChange={(e) => set('assignee', e.target.value)} className="h-8 w-auto text-[13px]">
              <option value="">Any technician</option>
              {technicians.data?.map((t) => <option key={t.id} value={t.id}>{t.fullName}</option>)}
            </Select>
          )}
          {staff && (
            <label className="flex h-8 cursor-pointer items-center gap-2 rounded-lg border border-line bg-surface px-2.5 text-[13px] text-ink-2">
              <input type="checkbox" checked={get('escalated') === '1'} onChange={(e) => set('escalated', e.target.checked ? '1' : null)} className="accent-[var(--accent)]" />
              Escalated
            </label>
          )}
          <div className="flex items-center gap-1.5 text-[13px] text-muted">
            <span>From</span>
            <Input type="date" aria-label="Reported from" value={get('from')} onChange={(e) => set('from', e.target.value)} className="h-8 w-auto text-[13px]" />
            <span>to</span>
            <Input type="date" aria-label="Reported to" value={get('to')} onChange={(e) => set('to', e.target.value)} className="h-8 w-auto text-[13px]" />
          </div>
          {activeFilters > 0 && (
            <Button size="sm" variant="ghost" icon={<X className="size-3.5" />} onClick={() => setParams({}, { replace: true })}>Clear filters</Button>
          )}
          <div className="ml-auto flex items-center gap-2">
            <Select aria-label="Sort" value={`${query.sort}:${query.direction}`} onChange={(e) => {
              const [s, d] = e.target.value.split(':')
              const next = new URLSearchParams(params); next.set('sort', s!); next.set('dir', d!); setParams(next, { replace: true })
            }} className="h-8 w-auto text-[13px]">
              <option value="createdAt:desc">Newest first</option>
              <option value="createdAt:asc">Oldest first</option>
              <option value="priority:asc">Most urgent first</option>
              <option value="updatedAt:desc">Recently updated</option>
            </Select>
          </div>
        </div>
      </div>

      <Panel bodyClassName={cn('px-4 py-2', list.isFetching && 'opacity-70 transition-opacity')}>
        {list.isLoading ? (
          <div className="space-y-2 py-2">{[0, 1, 2, 3, 4].map((i) => <Skeleton key={i} className="h-12" />)}</div>
        ) : (
          <IncidentList
            items={data?.items ?? []}
            showReporter={staff}
            empty={
              <EmptyState icon={<Inbox className="size-8" />} title={activeFilters ? 'No incidents match these filters' : 'No incidents yet'}
                action={activeFilters ? <Button onClick={() => setParams({}, { replace: true })}>Clear filters</Button> : undefined}>
                {activeFilters ? 'Try removing a filter or widening the date range.' : undefined}
              </EmptyState>
            }
          />
        )}
      </Panel>

      {data && data.totalItems > 0 && (
        <div className="mt-3 flex items-center justify-between text-[13px] text-muted">
          <span className="tabular">
            {data.page * data.size + 1}–{Math.min((data.page + 1) * data.size, data.totalItems)} of {data.totalItems}
          </span>
          <div className="flex gap-1">
            <Button size="sm" variant="ghost" disabled={data.page === 0} icon={<ChevronLeft className="size-4" />} onClick={() => set('page', String(data.page - 1))}>Previous</Button>
            <Button size="sm" variant="ghost" disabled={data.page + 1 >= data.totalPages} onClick={() => set('page', String(data.page + 1))}>Next<ChevronRight className="size-4" /></Button>
          </div>
        </div>
      )}
    </div>
  )
}
