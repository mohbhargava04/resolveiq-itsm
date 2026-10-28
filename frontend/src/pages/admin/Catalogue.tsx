import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Plus, ShieldAlert } from 'lucide-react'
import { api } from '@/lib/api'
import { cn, LEVEL_LABEL } from '@/lib/format'
import type { CategoryView, Level, ServiceView } from '@/lib/types'
import { Button, Dialog, ErrorNote, Field, Input, PageHeader, Panel, Select, Skeleton, Switch, Textarea } from '@/components/ui'

const LEVELS: Level[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']

export default function Catalogue() {
  const services = useQuery({ queryKey: ['services'], queryFn: () => api.get<ServiceView[]>('/services') })
  const categories = useQuery({ queryKey: ['categories', 'all'], queryFn: () => api.get<CategoryView[]>('/categories?includeInactive=true') })
  const [svc, setSvc] = useState<ServiceView | 'new' | null>(null)
  const [cat, setCat] = useState<CategoryView | 'new' | null>(null)
  const names = Object.fromEntries((services.data ?? []).map((s) => [s.code, s.name]))

  return (
    <div className="space-y-6">
      <PageHeader title="Services and categories" description="Changes apply immediately. New categories are learned by the AI classifier from their keywords; no code changes needed." />

      <Panel title="IT services" description="Criticality feeds the priority formula; dependencies drive root-cause correlation."
        action={<Button size="sm" icon={<Plus className="size-3.5" />} onClick={() => setSvc('new')}>Add service</Button>} bodyClassName="px-0 pb-2">
        {services.isLoading ? <Skeleton className="mx-5 h-48" /> : (
          <ul className="divide-y divide-line">
            {services.data?.map((s) => (
              <li key={s.code}>
                <button onClick={() => setSvc(s)} className={cn('grid w-full grid-cols-[minmax(0,1fr)_110px] gap-4 px-5 py-3 text-left hover:bg-surface-2/60 sm:grid-cols-[minmax(0,1.2fr)_110px_minmax(0,1fr)] cursor-pointer', !s.active && 'opacity-55')}>
                  <span className="min-w-0">
                    <span className="block font-medium">{s.name}</span>
                    <span className="block truncate text-xs text-muted">{s.ownerTeam}{s.userBase ? `, about ${s.userBase.toLocaleString()} users` : ''}</span>
                  </span>
                  <span className={cn('self-center text-[13px]', s.criticality === 'CRITICAL' ? 'font-medium text-p1' : s.criticality === 'HIGH' ? 'text-p2' : 'text-ink-2')}>
                    {LEVEL_LABEL[s.criticality]}
                  </span>
                  <span className="hidden self-center truncate text-xs text-muted sm:block">
                    {s.dependsOn.length ? `Depends on ${s.dependsOn.map((d) => names[d] ?? d).join(', ')}` : 'No dependencies'}
                  </span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </Panel>

      <Panel title="Incident categories" description="Keywords train the classifier; technicians' skills refer to these categories."
        action={<Button size="sm" icon={<Plus className="size-3.5" />} onClick={() => setCat('new')}>Add category</Button>} bodyClassName="px-0 pb-2">
        {categories.isLoading ? <Skeleton className="mx-5 h-48" /> : (
          <ul className="divide-y divide-line">
            {categories.data?.map((c) => (
              <li key={c.code}>
                <button onClick={() => setCat(c)} className={cn('flex w-full items-start gap-4 px-5 py-3 text-left hover:bg-surface-2/60 cursor-pointer', !c.active && 'opacity-55')}>
                  <span className="w-48 shrink-0">
                    <span className="flex items-center gap-1.5 font-medium">
                      {c.name}
                      {c.securitySensitive && <ShieldAlert className="size-3.5 text-p1" aria-label="Security-sensitive: never below P2" />}
                    </span>
                    <span className="text-xs text-muted">{c.code}</span>
                  </span>
                  <span className="flex min-w-0 flex-wrap gap-1">
                    {c.keywords.slice(0, 10).map((k) => <span key={k} className="rounded bg-surface-2 px-1.5 py-px text-[11px] text-ink-2">{k}</span>)}
                    {c.keywords.length > 10 && <span className="text-[11px] text-faint">+{c.keywords.length - 10} more</span>}
                  </span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </Panel>

      {svc && <ServiceDialog service={svc === 'new' ? null : svc} all={services.data ?? []} onClose={() => setSvc(null)} />}
      {cat && <CategoryDialog category={cat === 'new' ? null : cat} onClose={() => setCat(null)} />}
    </div>
  )
}

function ServiceDialog({ service, all, onClose }: { service: ServiceView | null; all: ServiceView[]; onClose: () => void }) {
  const qc = useQueryClient()
  const [f, setF] = useState({
    code: service?.code ?? '', name: service?.name ?? '', description: service?.description ?? '',
    criticality: service?.criticality ?? 'MEDIUM' as Level, ownerTeam: service?.ownerTeam ?? '',
    userBase: String(service?.userBase ?? 0), dependsOn: service?.dependsOn ?? [] as string[], active: service?.active ?? true,
  })
  const save = useMutation({
    mutationFn: () => {
      const body = { ...f, userBase: Number(f.userBase) || 0 }
      return service ? api.put(`/admin/services/${service.code}`, body) : api.post('/admin/services', body)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['services'] })
      toast.success(service ? 'Service updated' : 'Service added')
      onClose()
    },
  })
  const toggleDep = (code: string) => setF({ ...f, dependsOn: f.dependsOn.includes(code) ? f.dependsOn.filter((d) => d !== code) : [...f.dependsOn, code] })
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()} title={service ? service.name : 'Add a service'} width={560}
      footer={<><Button variant="ghost" onClick={onClose}>Cancel</Button><Button variant="primary" loading={save.isPending} onClick={() => save.mutate()}>Save</Button></>}>
      <div className="space-y-4">
        <div className="grid gap-4 sm:grid-cols-[140px_1fr]">
          <Field label="Code"><Input value={f.code} disabled={!!service} onChange={(e) => setF({ ...f, code: e.target.value })} placeholder="HR_PORTAL" /></Field>
          <Field label="Name"><Input value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} /></Field>
        </div>
        <Field label="Description"><Textarea rows={2} value={f.description} onChange={(e) => setF({ ...f, description: e.target.value })} /></Field>
        <div className="grid gap-4 sm:grid-cols-3">
          <Field label="Criticality"><Select value={f.criticality} onChange={(e) => setF({ ...f, criticality: e.target.value as Level })}>{LEVELS.map((l) => <option key={l} value={l}>{LEVEL_LABEL[l]}</option>)}</Select></Field>
          <Field label="Owner team"><Input value={f.ownerTeam} onChange={(e) => setF({ ...f, ownerTeam: e.target.value })} /></Field>
          <Field label="Users"><Input type="number" min={0} value={f.userBase} onChange={(e) => setF({ ...f, userBase: e.target.value })} /></Field>
        </div>
        <Field label="Depends on" hint="Cycles are rejected">
          <div className="flex flex-wrap gap-1.5">
            {all.filter((s) => s.code !== service?.code).map((s) => (
              <button key={s.code} type="button" onClick={() => toggleDep(s.code)} aria-pressed={f.dependsOn.includes(s.code)}
                className={cn('rounded-md border px-2 py-1 text-xs cursor-pointer', f.dependsOn.includes(s.code) ? 'border-accent bg-accent-soft font-medium text-accent-text' : 'border-line text-ink-2 hover:bg-surface-2')}>
                {s.name}
              </button>
            ))}
          </div>
        </Field>
        {service && (
          <label className="flex items-center justify-between rounded-lg border border-line px-3 py-2 text-[13px]">
            <span>Active (new incidents can be reported against it)</span>
            <Switch checked={f.active} onChange={(v) => setF({ ...f, active: v })} label="Active" />
          </label>
        )}
        {save.error && <ErrorNote error={save.error} />}
      </div>
    </Dialog>
  )
}

function CategoryDialog({ category, onClose }: { category: CategoryView | null; onClose: () => void }) {
  const qc = useQueryClient()
  const [f, setF] = useState({
    code: category?.code ?? '', name: category?.name ?? '', description: category?.description ?? '',
    keywords: (category?.keywords ?? []).join(', '), securitySensitive: category?.securitySensitive ?? false, active: category?.active ?? true,
  })
  const save = useMutation({
    mutationFn: () => {
      const body = { ...f, keywords: f.keywords.split(',').map((k) => k.trim()).filter(Boolean) }
      return category ? api.put(`/admin/categories/${category.code}`, body) : api.post('/admin/categories', body)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['categories'] })
      toast.success(category ? 'Category updated' : 'Category added; the classifier has learned it')
      onClose()
    },
  })
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()} title={category ? category.name : 'Add a category'} width={520}
      footer={<><Button variant="ghost" onClick={onClose}>Cancel</Button><Button variant="primary" loading={save.isPending} onClick={() => save.mutate()}>Save</Button></>}>
      <div className="space-y-4">
        <div className="grid gap-4 sm:grid-cols-[140px_1fr]">
          <Field label="Code"><Input value={f.code} disabled={!!category} onChange={(e) => setF({ ...f, code: e.target.value })} placeholder="IOT" /></Field>
          <Field label="Name"><Input value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} /></Field>
        </div>
        <Field label="Description"><Input value={f.description} onChange={(e) => setF({ ...f, description: e.target.value })} /></Field>
        <Field label="Keywords" hint="Comma-separated words people use when describing this kind of problem">
          <Textarea rows={3} value={f.keywords} onChange={(e) => setF({ ...f, keywords: e.target.value })} placeholder="projector, smart board, sensor" />
        </Field>
        <label className="flex items-center justify-between rounded-lg border border-line px-3 py-2 text-[13px]">
          <span>Security-sensitive (never prioritised below P2)</span>
          <Switch checked={f.securitySensitive} onChange={(v) => setF({ ...f, securitySensitive: v })} label="Security-sensitive" />
        </label>
        {category && (
          <label className="flex items-center justify-between rounded-lg border border-line px-3 py-2 text-[13px]">
            <span>Active</span>
            <Switch checked={f.active} onChange={(v) => setF({ ...f, active: v })} label="Active" />
          </label>
        )}
        {save.error && <ErrorNote error={save.error} />}
      </div>
    </Dialog>
  )
}
