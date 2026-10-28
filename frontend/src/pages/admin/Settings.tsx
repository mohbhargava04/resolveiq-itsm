import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Play } from 'lucide-react'
import { api } from '@/lib/api'
import { cn } from '@/lib/format'
import type { Settings as SettingsData, StrategyView } from '@/lib/types'
import { Button, PageHeader, Panel, Skeleton, Switch } from '@/components/ui'

export default function Settings() {
  const qc = useQueryClient()
  const q = useQuery({ queryKey: ['admin', 'settings'], queryFn: () => api.get<SettingsData>('/admin/settings') })
  const save = useMutation({
    mutationFn: (changes: Record<string, string>) => api.put<SettingsData>('/admin/settings', changes),
    onSuccess: (d) => { qc.setQueryData(['admin', 'settings'], d); toast.success('Saved; applies to the next incident') },
    onError: (e) => toast.error((e as Error).message),
  })
  const monitor = useMutation({
    mutationFn: () => api.post<{ scanned: number; warnings: number; breaches: number; escalations: number }>('/admin/sla-monitor/run'),
    onSuccess: (r) => {
      toast.success('SLA monitor ran', { description: `${r.scanned} active incidents checked: ${r.warnings} warnings, ${r.breaches} breaches, ${r.escalations} escalations.` })
      qc.invalidateQueries({ queryKey: ['dashboard'] })
    },
  })
  const s = q.data

  return (
    <div className="space-y-6">
      <PageHeader title="Automation" description="Choose how ResolveIQ prioritises and routes work. Changes take effect immediately, without a restart." />
      {!s ? <Skeleton className="h-96" /> : (
        <>
          <Panel title="Priority calculation" description="How impact, urgency, service criticality and affected users become P1–P4.">
            <StrategyChoice items={s.priorityStrategies} onPick={(k) => save.mutate({ PRIORITY_STRATEGY: k })} busy={save.isPending} />
          </Panel>
          <Panel title="Technician assignment" description="Used for automatic assignment, and as the default ranking when assigning by hand.">
            <StrategyChoice items={s.assignmentStrategies} onPick={(k) => save.mutate({ ASSIGNMENT_STRATEGY: k })} busy={save.isPending} />
          </Panel>
          <Panel title="Automatic actions">
            <ul className="divide-y divide-line">
              <Toggle title="Assign new incidents automatically" desc="Otherwise they wait in the team lead's queue." value={s.AUTO_ASSIGNMENT === 'true'} onChange={(v) => save.mutate({ AUTO_ASSIGNMENT: String(v) })} />
              <Toggle title="Escalate overdue incidents automatically" desc="Team lead, then system administrator, then IT manager." value={s.AUTO_ESCALATION === 'true'} onChange={(v) => save.mutate({ AUTO_ESCALATION: String(v) })} />
              <Toggle title="Close incidents once the reporter confirms the fix" desc="Skips the separate close step after verification." value={s.AUTO_CLOSE_VERIFIED === 'true'} onChange={(v) => save.mutate({ AUTO_CLOSE_VERIFIED: String(v) })} />
            </ul>
          </Panel>
          <Panel title="SLA monitor" description={`Checks every active incident every 30 seconds. Notifications go out by ${s.notificationChannels.map((c) => c === 'IN_APP' ? 'in-app' : c === 'EMAIL' ? 'email' : 'SMS').join(', ')}.`}
            action={<Button size="sm" icon={<Play className="size-3.5" />} loading={monitor.isPending} onClick={() => monitor.mutate()}>Run now</Button>}>
            <p className="text-[13px] text-muted">Running it manually is useful in a demo, so you don't have to wait for the next scheduled check.</p>
          </Panel>
        </>
      )}
    </div>
  )
}

function StrategyChoice({ items, onPick, busy }: { items: StrategyView[]; onPick: (key: string) => void; busy: boolean }) {
  return (
    <div className="grid gap-2 md:grid-cols-2" role="radiogroup">
      {items.map((it) => (
        <button key={it.key} role="radio" aria-checked={it.active} disabled={busy || it.active} onClick={() => onPick(it.key)}
          className={cn('rounded-lg border px-4 py-3 text-left transition-colors cursor-pointer disabled:cursor-default',
            it.active ? 'border-accent bg-accent-soft' : 'border-line hover:bg-surface-2')}>
          <span className="flex items-center justify-between gap-2">
            <span className="text-[13.5px] font-medium">{it.name}</span>
            <span className={cn('size-3.5 rounded-full border-2', it.active ? 'border-accent bg-accent shadow-[inset_0_0_0_2px_var(--surface)]' : 'border-line-strong')} />
          </span>
          <span className="mt-1 block text-xs text-muted">{it.description}</span>
        </button>
      ))}
    </div>
  )
}

function Toggle({ title, desc, value, onChange }: { title: string; desc: string; value: boolean; onChange: (v: boolean) => void }) {
  return (
    <li className="flex items-center justify-between gap-4 py-3">
      <span>
        <span className="block text-[13.5px] font-medium">{title}</span>
        <span className="block text-xs text-muted">{desc}</span>
      </span>
      <Switch checked={value} onChange={onChange} label={title} />
    </li>
  )
}
