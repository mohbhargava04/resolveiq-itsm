import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Bell, Mail, MessageSquareText } from 'lucide-react'
import { api } from '@/lib/api'
import { cn, when } from '@/lib/format'
import type { OutboxRow } from '@/lib/types'
import { PageHeader, Panel, Segmented, Skeleton } from '@/components/ui'

const ICON = { IN_APP: Bell, EMAIL: Mail, SMS: MessageSquareText }

export default function Outbox() {
  const [channel, setChannel] = useState<'ALL' | 'EMAIL' | 'SMS' | 'IN_APP'>('ALL')
  const q = useQuery({
    queryKey: ['outbox', channel],
    queryFn: () => api.get<OutboxRow[]>(`/admin/notifications/outbox?limit=150${channel === 'ALL' ? '' : `&channel=${channel}`}`),
    refetchInterval: 15_000,
  })
  return (
    <div>
      <PageHeader title="Delivery log" description="Every notification sent. Without SMTP or Twilio credentials, email and SMS are simulated and logged here instead of being sent." />
      <div className="mb-4 w-full max-w-sm">
        <Segmented name="Channel" value={channel} onChange={setChannel}
          options={[{ value: 'ALL', label: 'All' }, { value: 'EMAIL', label: 'Email' }, { value: 'SMS', label: 'SMS' }, { value: 'IN_APP', label: 'In-app' }]} />
      </div>
      <Panel bodyClassName="p-0">
        {q.isLoading ? <Skeleton className="m-5 h-80" /> : (
          <ul className="divide-y divide-line">
            {q.data?.map((n) => {
              const Icon = ICON[n.channel]
              return (
                <li key={n.id} className="grid grid-cols-[28px_minmax(0,1fr)_auto] items-start gap-3 px-5 py-3">
                  <span className={cn('mt-0.5 flex size-7 items-center justify-center rounded-md', n.urgent ? 'bg-p1-soft text-p1' : 'bg-surface-2 text-muted')}><Icon className="size-3.5" /></span>
                  <span className="min-w-0">
                    <span className="block truncate text-[13.5px] font-medium">{n.subject}</span>
                    <span className="block truncate text-xs text-muted">
                      To {n.recipient}{n.address ? ` (${n.address})` : ''}
                      {n.incidentNumber && <> about <Link to={`/incidents/${n.incidentNumber}`} className="tabular text-accent-text hover:underline">{n.incidentNumber}</Link></>}
                    </span>
                    {n.error && <span className="block text-xs text-p1">{n.error}</span>}
                  </span>
                  <span className="text-right">
                    <span className={cn('block text-xs font-medium', n.status === 'FAILED' ? 'text-p1' : n.status === 'SENT' ? 'text-ok' : 'text-muted')}>
                      {n.status === 'SIMULATED' ? 'Simulated' : n.status === 'SENT' ? 'Delivered' : 'Failed'}
                    </span>
                    <span className="block text-xs tabular text-faint">{when(n.createdAt)}</span>
                  </span>
                </li>
              )
            })}
          </ul>
        )}
      </Panel>
    </div>
  )
}
