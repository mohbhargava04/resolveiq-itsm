import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Play } from 'lucide-react'
import { api } from '@/lib/api'
import { cn } from '@/lib/format'
import type { Settings as SettingsData, StrategyView } from '@/lib/types'
import { Button, Field, Input, PageHeader, Panel, Skeleton, Switch } from '@/components/ui'

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
          <Panel title="Sign-in and access" description="Who can join, and how strongly accounts are protected.">
            <DomainsField value={s.ALLOWED_EMAIL_DOMAINS} busy={save.isPending} onSave={(v) => save.mutate({ ALLOWED_EMAIL_DOMAINS: v })} />
            <ul className="mt-3 divide-y divide-line border-t border-line">
              <Toggle title="Create accounts on first Google sign-in" desc="New people from an allowed domain get an Employee account. Off: only people an administrator added can sign in." value={s.AUTO_PROVISION_SSO === 'true'} onChange={(v) => save.mutate({ AUTO_PROVISION_SSO: String(v) })} />
              <Toggle title="Allow self-registration with a password" desc="Employees from an allowed domain can create an account on the register page." value={s.SELF_REGISTRATION === 'true'} onChange={(v) => save.mutate({ SELF_REGISTRATION: String(v) })} />
              <Toggle title="Require two-step verification for administrators and IT managers" desc="They are asked to set it up at their next sign-in and cannot turn it off." value={s.REQUIRE_MFA_FOR_PRIVILEGED === 'true'} onChange={(v) => save.mutate({ REQUIRE_MFA_FOR_PRIVILEGED: String(v) })} />
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

function DomainsField({ value, onSave, busy }: { value: string; onSave: (v: string) => void; busy: boolean }) {
  const [text, setText] = useState(value.split(',').join(', '))
  useEffect(() => setText(value.split(',').filter(Boolean).join(', ')), [value])
  return (
    <form onSubmit={(e) => { e.preventDefault(); onSave(text) }} className="flex flex-wrap items-end gap-2">
      <Field label="Company e-mail domains" htmlFor="domains" className="min-w-64 flex-1"
        hint="Only these addresses can sign in with Google or register. Sub-domains are included. Empty allows any domain.">
        <Input id="domains" value={text} onChange={(e) => setText(e.target.value)} placeholder="resolveiq.test, hyderabad.bits-pilani.ac.in" />
      </Field>
      <Button type="submit" loading={busy} className="mb-[38px]">Save domains</Button>
    </form>
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
