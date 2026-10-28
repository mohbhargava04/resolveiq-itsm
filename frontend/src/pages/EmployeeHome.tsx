import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { CircleCheck, Inbox, MessageCircleQuestion, Plus } from 'lucide-react'
import { api } from '@/lib/api'
import { useUser } from '@/lib/auth'
import { ago } from '@/lib/format'
import type { Dashboard } from '@/lib/types'
import { Button, EmptyState, Panel, Skeleton } from '@/components/ui'
import { IncidentList, PriorityBadge } from '@/components/incident-bits'

function greeting() {
  const h = new Date().getHours()
  return h < 12 ? 'Good morning' : h < 17 ? 'Good afternoon' : 'Good evening'
}

export default function EmployeeHome() {
  const user = useUser()
  const { data, isLoading } = useQuery({ queryKey: ['dashboard'], queryFn: () => api.get<Dashboard>('/dashboard') })
  const first = user.fullName.split(' ')[0]

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-[26px] font-semibold tracking-tight">{greeting()}, {first}</h1>
          <p className="mt-1 text-sm text-muted">
            {isLoading ? 'Loading your incidents…' :
              data?.open ? `You have ${data.open} open ${data.open === 1 ? 'incident' : 'incidents'}. We'll notify you as they move.` :
              'Nothing open right now. Everything you reported has been taken care of.'}
          </p>
        </div>
        <Link to="/report"><Button variant="primary" icon={<Plus className="size-4" />}>Report an incident</Button></Link>
      </div>

      {(isLoading || (data?.needsMyAction?.length ?? 0) > 0) && (
        <section aria-labelledby="action-heading">
          <h2 id="action-heading" className="mb-2 text-[15px] font-semibold">Waiting for you</h2>
          {isLoading ? <Skeleton className="h-20" /> : (
            <ul className="grid gap-3 md:grid-cols-2">
              {data!.needsMyAction!.map((i) => {
                const asking = i.status === 'AWAITING_INFO'
                return (
                  <li key={i.number}>
                    <Link
                      to={`/incidents/${i.number}`}
                      className="flex h-full gap-3 rounded-xl border border-line bg-surface p-4 transition-colors hover:border-line-strong"
                    >
                      <span className={asking ? 'text-p2' : 'text-ok'}>
                        {asking ? <MessageCircleQuestion className="size-5" /> : <CircleCheck className="size-5" />}
                      </span>
                      <span className="min-w-0">
                        <span className="block text-[13px] font-medium text-ink-2">
                          {asking ? 'The technician needs more information' : 'Fixed — please confirm it works'}
                        </span>
                        <span className="mt-0.5 block truncate font-medium">{i.title}</span>
                        <span className="mt-1 flex items-center gap-2 text-xs text-muted">
                          <PriorityBadge priority={i.priority} />
                          <span className="tabular">{i.number}</span>
                          <span>updated {ago(i.updatedAt)}</span>
                        </span>
                      </span>
                    </Link>
                  </li>
                )
              })}
            </ul>
          )}
        </section>
      )}

      <Panel
        title="Your incidents"
        description={data ? `${data.open ?? 0} open, ${data.resolved ?? 0} resolved` : undefined}
        action={<Link to="/incidents" className="text-[13px] font-medium text-accent-text hover:underline">See all</Link>}
      >
        {isLoading ? (
          <div className="space-y-2">{[0, 1, 2].map((i) => <Skeleton key={i} className="h-12" />)}</div>
        ) : (
          <IncidentList
            items={data?.recent ?? []}
            empty={
              <EmptyState icon={<Inbox className="size-8" />} title="You haven't reported anything yet"
                action={<Link to="/report"><Button variant="primary">Report an incident</Button></Link>}>
                When something stops working, report it here and follow it until it's fixed.
              </EmptyState>
            }
          />
        )}
      </Panel>
    </div>
  )
}
