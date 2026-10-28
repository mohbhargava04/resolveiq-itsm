import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { api, qs } from '@/lib/api'
import { fullDate, humanise } from '@/lib/format'
import { useDebounced } from '@/lib/useDebounced'
import type { AuditEntry, Page } from '@/lib/types'
import { Button, Input, PageHeader, Panel, Select, Skeleton } from '@/components/ui'

const ACTIONS = [
  'INCIDENT_CREATED', 'INCIDENT_CLASSIFIED', 'PRIORITY_CALCULATED', 'PRIORITY_CHANGED', 'INCIDENT_ASSIGNED', 'INCIDENT_REASSIGNED',
  'STATUS_CHANGED', 'NOTE_ADDED', 'INFO_REQUESTED', 'INFO_PROVIDED', 'SLA_WARNING', 'SLA_BREACHED', 'INCIDENT_ESCALATED',
  'DUPLICATE_SUSPECTED', 'DUPLICATE_LINKED', 'FEEDBACK_SUBMITTED', 'USER_CREATED', 'USER_UPDATED', 'USER_DEACTIVATED',
  'SERVICE_CHANGED', 'CATEGORY_CHANGED', 'SLA_POLICY_CHANGED', 'SETTING_CHANGED', 'LOGIN_SUCCEEDED', 'LOGIN_FAILED', 'ACCOUNT_LOCKED',
]

export default function AuditLog() {
  const [incident, setIncident] = useState('')
  const [actor, setActor] = useState('')
  const [action, setAction] = useState('')
  const [page, setPage] = useState(0)
  const di = useDebounced(incident, 300)
  const da = useDebounced(actor, 300)
  const path = `/audit${qs({ incident: di, actor: da, action, page, size: 30 })}`
  const q = useQuery({ queryKey: ['audit', path], queryFn: () => api.get<Page<AuditEntry>>(path), placeholderData: (p) => p })

  return (
    <div>
      <PageHeader title="Audit log" description="Every action in the system, append-only. Records cannot be edited or deleted." />
      <div className="mb-4 flex flex-wrap gap-2">
        <Input aria-label="Incident" value={incident} onChange={(e) => { setIncident(e.target.value); setPage(0) }} placeholder="Incident number" className="w-40" />
        <Input aria-label="Person" value={actor} onChange={(e) => { setActor(e.target.value); setPage(0) }} placeholder="Person" className="w-48" />
        <Select aria-label="Action" value={action} onChange={(e) => { setAction(e.target.value); setPage(0) }} className="w-auto min-w-48">
          <option value="">Every action</option>
          {ACTIONS.map((a) => <option key={a} value={a}>{humanise(a)}</option>)}
        </Select>
      </div>
      <Panel bodyClassName="p-0">
        {q.isLoading ? <Skeleton className="m-5 h-80" /> : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[820px] text-[13px]">
              <thead className="border-b border-line text-left text-xs text-muted">
                <tr>
                  <th className="px-5 py-2.5 font-medium">When</th>
                  <th className="py-2.5 font-medium">Who</th>
                  <th className="py-2.5 font-medium">Action</th>
                  <th className="py-2.5 font-medium">Incident</th>
                  <th className="py-2.5 font-medium">Change</th>
                  <th className="px-5 py-2.5 font-medium">Description</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {q.data?.items.map((a) => (
                  <tr key={a.id} className="align-top">
                    <td className="whitespace-nowrap px-5 py-2.5 tabular text-muted">{fullDate(a.occurredAt)}</td>
                    <td className="py-2.5">
                      <div>{a.actor}</div>
                      {a.actorRole && <div className="text-xs text-muted">{humanise(a.actorRole)}</div>}
                    </td>
                    <td className="whitespace-nowrap py-2.5">{humanise(a.action)}</td>
                    <td className="py-2.5">{a.incidentNumber ? <Link to={`/incidents/${a.incidentNumber}`} className="tabular text-accent-text hover:underline">{a.incidentNumber}</Link> : <span className="text-faint">—</span>}</td>
                    <td className="whitespace-nowrap py-2.5 text-xs text-muted">{a.previousState || a.newState ? `${a.previousState ? humanise(a.previousState) : '∅'} → ${a.newState ? humanise(a.newState) : '∅'}` : ''}</td>
                    <td className="max-w-md px-5 py-2.5 text-ink-2">{a.description}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Panel>
      {q.data && q.data.totalPages > 1 && (
        <div className="mt-3 flex items-center justify-between text-[13px] text-muted">
          <span className="tabular">Page {q.data.page + 1} of {q.data.totalPages}, {q.data.totalItems} records</span>
          <div className="flex gap-1">
            <Button size="sm" variant="ghost" disabled={page === 0} icon={<ChevronLeft className="size-4" />} onClick={() => setPage(page - 1)}>Previous</Button>
            <Button size="sm" variant="ghost" disabled={page + 1 >= q.data.totalPages} onClick={() => setPage(page + 1)}>Next<ChevronRight className="size-4" /></Button>
          </div>
        </div>
      )}
    </div>
  )
}
