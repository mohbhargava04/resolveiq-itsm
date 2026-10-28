import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Check } from 'lucide-react'
import { api } from '@/lib/api'
import { cn } from '@/lib/format'
import type { IncidentDetail, Recommendation } from '@/lib/types'
import { Avatar, Button, Dialog, ErrorNote, Select, Skeleton } from './ui'

const STRATEGIES = [
  { key: '', label: 'Active strategy' },
  { key: 'SMART', label: 'Smart recommendation' },
  { key: 'SKILL', label: 'Skill-based' },
  { key: 'LOAD', label: 'Load-based' },
  { key: 'ROUND_ROBIN', label: 'Round-robin' },
  { key: 'EXPERIENCE', label: 'Experience-based' },
]

/** Ranked technician recommendations with the reasons behind each score. */
export function AssignDialog({ number, open, onOpenChange }: { number: string; open: boolean; onOpenChange: (o: boolean) => void }) {
  const qc = useQueryClient()
  const [strategy, setStrategy] = useState('')
  const [chosen, setChosen] = useState<number | null>(null)

  const recs = useQuery({
    queryKey: ['incident', number, 'recommendations', strategy],
    queryFn: () => api.get<Recommendation[]>(`/incidents/${number}/recommendations${strategy ? `?strategy=${strategy}` : ''}`),
    enabled: open,
  })

  const assign = useMutation({
    mutationFn: (technicianId: number) => api.post<IncidentDetail>(`/incidents/${number}/assign`, { technicianId }),
    onSuccess: (d) => {
      toast.success(`Assigned to ${d.assignee?.name}`, { description: `${number} is now in their queue.` })
      qc.invalidateQueries({ queryKey: ['incident', number] })
      qc.invalidateQueries({ queryKey: ['incidents'] })
      qc.invalidateQueries({ queryKey: ['dashboard'] })
      onOpenChange(false)
    },
  })

  const selected = chosen ?? recs.data?.[0]?.technician.id ?? null

  return (
    <Dialog
      open={open}
      onOpenChange={(o) => { onOpenChange(o); if (!o) setChosen(null) }}
      title={`Assign ${number}`}
      description="Ranked by the assignment engine. Hover a reason to see how the score was built."
      width={560}
      footer={
        <>
          <Button variant="ghost" onClick={() => onOpenChange(false)}>Cancel</Button>
          <Button variant="primary" disabled={!selected} loading={assign.isPending} onClick={() => selected && assign.mutate(selected)}>
            Assign
          </Button>
        </>
      }
    >
      <div className="mb-3 flex items-center gap-2">
        <span className="whitespace-nowrap text-[13px] text-muted">Rank using</span>
        <Select value={strategy} onChange={(e) => { setStrategy(e.target.value); setChosen(null) }} className="h-8 w-56 text-[13px]">
          {STRATEGIES.map((s) => <option key={s.key} value={s.key}>{s.label}</option>)}
        </Select>
      </div>
      {recs.isLoading && <div className="space-y-2">{[0, 1, 2].map((i) => <Skeleton key={i} className="h-16" />)}</div>}
      {recs.data?.length === 0 && (
        <p className="rounded-lg bg-surface-2 p-4 text-[13px] text-muted">No technician is eligible with this strategy. Everyone may be off duty or at capacity; try load-based ranking.</p>
      )}
      <ul className="space-y-1.5" role="radiogroup" aria-label="Technicians">
        {recs.data?.map((r, idx) => {
          const active = selected === r.technician.id
          return (
            <li key={r.technician.id}>
              <button
                role="radio"
                aria-checked={active}
                onClick={() => setChosen(r.technician.id)}
                className={cn(
                  'flex w-full items-start gap-3 rounded-lg border px-3 py-2.5 text-left transition-colors cursor-pointer',
                  active ? 'border-accent bg-accent-soft' : 'border-line hover:bg-surface-2',
                )}
              >
                <Avatar name={r.technician.name} size={30} />
                <span className="min-w-0 flex-1">
                  <span className="flex items-center gap-2">
                    <span className="text-[13.5px] font-medium">{r.technician.name}</span>
                    {idx === 0 && <span className="rounded bg-accent px-1.5 py-px text-[10px] font-semibold text-accent-ink">Best match</span>}
                    <span className="ml-auto text-xs tabular text-muted">{r.activeIncidents}/{r.capacity} active</span>
                  </span>
                  <span className="mt-1 flex flex-wrap gap-1">
                    {r.reasons.map((reason) => (
                      <span key={reason} className="rounded bg-surface-2 px-1.5 py-0.5 text-[11px] text-ink-2">{reason}</span>
                    ))}
                  </span>
                </span>
                <span className="flex flex-col items-end">
                  <span className="text-sm font-semibold tabular">{r.score.toFixed(0)}</span>
                  {active && <Check className="mt-1 size-4 text-accent-text" />}
                </span>
              </button>
            </li>
          )
        })}
      </ul>
      {assign.error && <div className="mt-3"><ErrorNote error={assign.error} /></div>}
    </Dialog>
  )
}
