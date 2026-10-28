import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Plus } from 'lucide-react'
import { api } from '@/lib/api'
import { cn, duration } from '@/lib/format'
import type { Priority, ServiceView, SlaPolicyView } from '@/lib/types'
import { Button, Dialog, ErrorNote, Field, Input, PageHeader, Panel, Select, Skeleton, Switch } from '@/components/ui'
import { PriorityBadge } from '@/components/incident-bits'

export default function SlaPolicies() {
  const policies = useQuery({ queryKey: ['sla-policies'], queryFn: () => api.get<SlaPolicyView[]>('/sla-policies') })
  const [editing, setEditing] = useState<SlaPolicyView | 'new' | null>(null)
  const defaults = policies.data?.filter((p) => !p.serviceCode) ?? []
  const specific = policies.data?.filter((p) => p.serviceCode) ?? []

  const table = (rows: SlaPolicyView[]) => (
    <table className="w-full text-[13px]">
      <thead className="text-left text-xs text-muted">
        <tr>
          <th className="pb-2 font-medium">Priority</th>
          <th className="pb-2 font-medium">Applies to</th>
          <th className="pb-2 text-right font-medium">First response</th>
          <th className="pb-2 text-right font-medium">Resolution</th>
          <th className="pb-2 text-right font-medium">Warn at</th>
        </tr>
      </thead>
      <tbody className="divide-y divide-line">
        {rows.map((p) => (
          <tr key={p.id} onClick={() => setEditing(p)} className={cn('cursor-pointer hover:bg-surface-2/50', !p.active && 'opacity-50')}>
            <td className="py-2.5"><PriorityBadge priority={p.priority} showLabel /></td>
            <td className="py-2.5">{p.serviceName}</td>
            <td className="py-2.5 text-right tabular">{duration(p.responseMinutes)}</td>
            <td className="py-2.5 text-right tabular">{duration(p.resolutionMinutes)}</td>
            <td className="py-2.5 text-right tabular text-muted">{p.warningThresholdPercent}%</td>
          </tr>
        ))}
      </tbody>
    </table>
  )

  return (
    <div className="space-y-6">
      <PageHeader title="SLA policies" description="A service-specific policy overrides the default for the same priority. Edits apply to new and re-prioritised incidents; deadlines already running keep the targets they started with."
        actions={<Button variant="primary" icon={<Plus className="size-4" />} onClick={() => setEditing('new')}>Add service policy</Button>} />
      <Panel title="Organisation defaults">{policies.isLoading ? <Skeleton className="h-40" /> : table(defaults)}</Panel>
      <Panel title="Service-specific agreements">{policies.isLoading ? <Skeleton className="h-28" /> : specific.length ? table(specific) : <p className="text-[13px] text-muted">None yet.</p>}</Panel>
      {editing && <PolicyDialog policy={editing === 'new' ? null : editing} onClose={() => setEditing(null)} />}
    </div>
  )
}

function PolicyDialog({ policy, onClose }: { policy: SlaPolicyView | null; onClose: () => void }) {
  const qc = useQueryClient()
  const services = useQuery({ queryKey: ['services'], queryFn: () => api.get<ServiceView[]>('/services') })
  const [f, setF] = useState({
    priority: (policy?.priority ?? 'P1') as Priority, serviceCode: policy?.serviceCode ?? '',
    responseMinutes: String(policy?.responseMinutes ?? 15), resolutionMinutes: String(policy?.resolutionMinutes ?? 120),
    warningThresholdPercent: String(policy?.warningThresholdPercent ?? 75), active: policy?.active ?? true,
  })
  const save = useMutation({
    mutationFn: () => {
      const body = {
        name: policy?.name, priority: f.priority, serviceCode: f.serviceCode || undefined,
        responseMinutes: Number(f.responseMinutes), resolutionMinutes: Number(f.resolutionMinutes),
        warningThresholdPercent: Number(f.warningThresholdPercent), active: f.active,
      }
      return policy ? api.put(`/admin/sla-policies/${policy.id}`, body) : api.post('/admin/sla-policies', body)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['sla-policies'] })
      toast.success('SLA policy saved')
      onClose()
    },
  })
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()} title={policy ? policy.name : 'Add a service policy'}
      footer={<><Button variant="ghost" onClick={onClose}>Cancel</Button><Button variant="primary" loading={save.isPending} onClick={() => save.mutate()}>Save</Button></>}>
      <div className="space-y-4">
        {!policy && (
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Priority">
              <Select value={f.priority} onChange={(e) => setF({ ...f, priority: e.target.value as Priority })}>
                {(['P1', 'P2', 'P3', 'P4'] as Priority[]).map((p) => <option key={p} value={p}>{p}</option>)}
              </Select>
            </Field>
            <Field label="Service">
              <Select value={f.serviceCode} onChange={(e) => setF({ ...f, serviceCode: e.target.value })}>
                <option value="">Choose a service</option>
                {services.data?.map((s) => <option key={s.code} value={s.code}>{s.name}</option>)}
              </Select>
            </Field>
          </div>
        )}
        <div className="grid gap-4 sm:grid-cols-3">
          <Field label="Response (min)"><Input type="number" min={1} value={f.responseMinutes} onChange={(e) => setF({ ...f, responseMinutes: e.target.value })} /></Field>
          <Field label="Resolution (min)"><Input type="number" min={1} value={f.resolutionMinutes} onChange={(e) => setF({ ...f, resolutionMinutes: e.target.value })} /></Field>
          <Field label="Warn at (%)"><Input type="number" min={10} max={95} value={f.warningThresholdPercent} onChange={(e) => setF({ ...f, warningThresholdPercent: e.target.value })} /></Field>
        </div>
        {policy?.serviceCode && (
          <label className="flex items-center justify-between rounded-lg border border-line px-3 py-2 text-[13px]">
            <span>Active</span><Switch checked={f.active} onChange={(v) => setF({ ...f, active: v })} label="Active" />
          </label>
        )}
        {save.error && <ErrorNote error={save.error} />}
      </div>
    </Dialog>
  )
}
