import { useMemo, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Copy, Sparkles, Timer } from 'lucide-react'
import { api, ApiError } from '@/lib/api'
import { cn, duration, LEVEL_LABEL, PRIORITY_LABEL, STATUS_LABEL } from '@/lib/format'
import { useDebounced } from '@/lib/useDebounced'
import type { CategorySuggestion, CategoryView, Level, PriorityPreview, ReportResponse, ServiceView, SimilarIncident } from '@/lib/types'
import { Button, ErrorNote, Field, Input, PageHeader, Segmented, Select, Skeleton, Textarea } from '@/components/ui'
import { PriorityBadge } from '@/components/incident-bits'

const IMPACT_HINT: Record<Level, string> = {
  LOW: 'Just me, or a minor inconvenience',
  MEDIUM: 'My team or department',
  HIGH: 'Several departments or a key process',
  CRITICAL: 'The whole organisation, or customers',
}
const URGENCY_HINT: Record<Level, string> = {
  LOW: 'It can wait; I have a workaround',
  MEDIUM: 'Needed today',
  HIGH: 'Needed within hours; work is blocked',
  CRITICAL: 'Needed now; money or safety at stake',
}
const LEVELS: Level[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']

export default function ReportIncident() {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [serviceCode, setServiceCode] = useState('')
  const [categoryCode, setCategoryCode] = useState('')
  const [impact, setImpact] = useState<Level>('MEDIUM')
  const [urgency, setUrgency] = useState<Level>('MEDIUM')
  const [affectedUsers, setAffectedUsers] = useState('1')
  const [duplicates, setDuplicates] = useState<{ number: string; title: string; status: string; score: number }[] | null>(null)

  const services = useQuery({ queryKey: ['services'], queryFn: () => api.get<ServiceView[]>('/services'), staleTime: 300_000 })
  const categories = useQuery({ queryKey: ['categories'], queryFn: () => api.get<CategoryView[]>('/categories'), staleTime: 300_000 })

  const text = useDebounced(`${title}\n${description}`.trim(), 450)
  const users = Math.max(1, Number(affectedUsers) || 1)

  // --- live assistance -----------------------------------------------------------------------
  const suggestion = useQuery({
    queryKey: ['assist', 'classify', text],
    queryFn: () => api.post<CategorySuggestion>('/intelligence/classify', { text }),
    enabled: text.length >= 12,
    placeholderData: (p) => p,
  })
  const effectiveCategory = categoryCode || (suggestion.data && suggestion.data.confidence >= 0.2 ? suggestion.data.categoryCode : undefined)

  const preview = useQuery({
    queryKey: ['assist', 'priority', serviceCode, effectiveCategory, impact, urgency, users],
    queryFn: () => api.post<PriorityPreview>('/intelligence/priority-preview', { serviceCode, categoryCode: effectiveCategory, impact, urgency, affectedUsers: users }),
    enabled: !!serviceCode,
    placeholderData: (p) => p,
  })

  const similar = useQuery({
    queryKey: ['assist', 'duplicates', serviceCode, text],
    queryFn: () => api.post<SimilarIncident[]>('/intelligence/duplicates', { title, description, serviceCode }),
    enabled: !!serviceCode && text.length >= 12,
    placeholderData: (p) => p,
  })

  const report = useMutation({
    mutationFn: (acknowledgeDuplicates: boolean) => api.post<ReportResponse>('/incidents', {
      title, description, serviceCode, categoryCode: categoryCode || undefined, impact, urgency,
      affectedUsers: users, acknowledgeDuplicates,
    }),
    onSuccess: (res) => {
      qc.invalidateQueries({ queryKey: ['dashboard'] })
      qc.invalidateQueries({ queryKey: ['incidents'] })
      toast.success(`${res.incident.number} reported`, {
        description: `${res.incident.priority} ${res.incident.priorityLabel}. ${res.assignmentMessage ?? ''}`.trim(),
      })
      navigate(`/incidents/${res.incident.number}`)
    },
    onError: (err) => {
      if (err instanceof ApiError && err.code === 'DUPLICATE_INCIDENT') {
        setDuplicates((err.details.matches as typeof duplicates) ?? [])
      }
    },
  })

  const fieldErrors = report.error instanceof ApiError ? report.error.fieldErrors ?? {} : {}
  const submit = (e: FormEvent) => {
    e.preventDefault()
    setDuplicates(null)
    report.mutate(false)
  }

  const servicesByCriticality = useMemo(
    () => [...(services.data ?? [])].filter((s) => s.active).sort((a, b) => a.name.localeCompare(b.name)),
    [services.data],
  )

  return (
    <div>
      <PageHeader title="Report an incident" description="Describe what's wrong in your own words. ResolveIQ works out the category, priority and deadline as you type." />

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_340px]">
        <form onSubmit={submit} className="space-y-5 rounded-xl border border-line bg-surface p-5 sm:p-6">
          <Field label="What's the problem?" htmlFor="title" error={fieldErrors.title}>
            <Input id="title" value={title} onChange={(e) => setTitle(e.target.value)} maxLength={160} required
              placeholder="e.g. Fee payment fails at checkout" autoFocus />
          </Field>

          <Field label="Details" htmlFor="desc" hint="What did you try, what did you see, when did it start?" error={fieldErrors.description}>
            <Textarea id="desc" rows={5} value={description} onChange={(e) => setDescription(e.target.value)} maxLength={4000} required
              placeholder="The payment page spins for about a minute and then shows 'transaction failed'. It started around 10:15." />
          </Field>

          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Affected service" htmlFor="service" error={fieldErrors.serviceCode}>
              <Select id="service" value={serviceCode} onChange={(e) => setServiceCode(e.target.value)} required>
                <option value="" disabled>Choose a service</option>
                {servicesByCriticality.map((s) => <option key={s.code} value={s.code}>{s.name}</option>)}
              </Select>
            </Field>
            <Field label="Category" htmlFor="category" hint={!categoryCode && suggestion.data?.categoryName ? `Suggested: ${suggestion.data.categoryName}` : 'Optional'}>
              <Select id="category" value={categoryCode} onChange={(e) => setCategoryCode(e.target.value)}>
                <option value="">Let ResolveIQ decide</option>
                {categories.data?.map((c) => <option key={c.code} value={c.code}>{c.name}</option>)}
              </Select>
            </Field>
          </div>

          <Field label="Who is affected?" hint={IMPACT_HINT[impact]}>
            <Segmented name="Impact" value={impact} onChange={setImpact} options={LEVELS.map((l) => ({ value: l, label: LEVEL_LABEL[l], hint: IMPACT_HINT[l] }))} />
          </Field>

          <Field label="How urgent is it?" hint={URGENCY_HINT[urgency]}>
            <Segmented name="Urgency" value={urgency} onChange={setUrgency} options={LEVELS.map((l) => ({ value: l, label: LEVEL_LABEL[l], hint: URGENCY_HINT[l] }))} />
          </Field>

          <Field label="How many people are affected?" htmlFor="users" className="max-w-[260px]" error={fieldErrors.affectedUsers}>
            <Input id="users" type="number" min={1} max={1000000} value={affectedUsers} onChange={(e) => setAffectedUsers(e.target.value)} className="tabular" />
          </Field>

          {duplicates && (
            <div className="rounded-lg border border-p2/40 bg-p2-soft p-4">
              <p className="text-[13.5px] font-medium text-ink">You already have an open incident about this</p>
              <ul className="mt-2 space-y-1">
                {duplicates.map((d) => (
                  <li key={d.number} className="text-[13px]">
                    <Link to={`/incidents/${d.number}`} className="font-medium text-accent-text hover:underline">{d.number}</Link>{' '}
                    {d.title} <span className="text-muted">({STATUS_LABEL[d.status as keyof typeof STATUS_LABEL] ?? d.status})</span>
                  </li>
                ))}
              </ul>
              <div className="mt-3 flex gap-2">
                <Button size="sm" onClick={() => navigate(`/incidents/${duplicates[0]?.number}`)}>Open existing incident</Button>
                <Button size="sm" variant="ghost" type="button" onClick={() => report.mutate(true)} loading={report.isPending}>It's a different problem, report anyway</Button>
              </div>
            </div>
          )}
          {report.error && !duplicates && !Object.keys(fieldErrors).length ? <ErrorNote error={report.error} /> : null}

          <div className="flex items-center justify-end gap-2 border-t border-line pt-5">
            <Button type="button" variant="ghost" onClick={() => navigate(-1)}>Cancel</Button>
            <Button type="submit" variant="primary" loading={report.isPending && !duplicates}>Report incident</Button>
          </div>
        </form>

        <aside className="space-y-4 lg:sticky lg:top-20 lg:self-start" aria-live="polite">
          <AssistCard title="Priority and deadline" icon={<Timer className="size-4" />}>
            {!serviceCode ? (
              <p className="text-[13px] text-muted">Choose the affected service to see the priority this incident will get.</p>
            ) : !preview.data ? <Skeleton className="h-20" /> : (
              <div className={cn('transition-opacity', preview.isFetching && 'opacity-60')}>
                <div className="flex items-center gap-2">
                  <PriorityBadge priority={preview.data.assessment.priority} showLabel className="h-7 px-2 text-[13px]" />
                  <span className="text-xs text-muted tabular">score {preview.data.assessment.score.toFixed(1)}</span>
                </div>
                <dl className="mt-3 grid grid-cols-2 gap-2 text-[13px]">
                  <div className="rounded-lg bg-surface-2 px-3 py-2">
                    <dt className="text-xs text-muted">First response</dt>
                    <dd className="font-semibold tabular">{duration(preview.data.responseMinutes)}</dd>
                  </div>
                  <div className="rounded-lg bg-surface-2 px-3 py-2">
                    <dt className="text-xs text-muted">Resolution</dt>
                    <dd className="font-semibold tabular">{duration(preview.data.resolutionMinutes)}</dd>
                  </div>
                </dl>
                <details className="mt-3 text-xs text-muted">
                  <summary className="cursor-pointer select-none hover:text-ink">Why {preview.data.assessment.priority} {PRIORITY_LABEL[preview.data.assessment.priority].toLowerCase()}?</summary>
                  <ul className="mt-2 space-y-1">{preview.data.assessment.factors.map((f) => <li key={f}>{f}</li>)}</ul>
                  <p className="mt-2">SLA policy: {preview.data.slaPolicy}</p>
                </details>
              </div>
            )}
          </AssistCard>

          <AssistCard title="Category" icon={<Sparkles className="size-4" />}>
            {categoryCode ? (
              <p className="text-[13px] text-muted">You chose {categories.data?.find((c) => c.code === categoryCode)?.name}.</p>
            ) : text.length < 12 ? (
              <p className="text-[13px] text-muted">Start describing the problem and a category will be suggested.</p>
            ) : !suggestion.data ? <Skeleton className="h-12" /> : suggestion.data.categoryCode ? (
              <div className={cn('transition-opacity', suggestion.isFetching && 'opacity-60')}>
                <div className="flex items-baseline justify-between gap-2">
                  <span className="font-medium">{suggestion.data.categoryName}</span>
                  <span className="text-xs tabular text-muted">{Math.round(suggestion.data.confidence * 100)}% sure</span>
                </div>
                <div className="mt-2 h-1 overflow-hidden rounded-full bg-surface-3">
                  <div className="h-full rounded-full bg-accent transition-all" style={{ width: `${suggestion.data.confidence * 100}%` }} />
                </div>
                {suggestion.data.signals.length > 0 && (
                  <p className="mt-2 text-xs text-muted">Based on: {suggestion.data.signals.join(', ')}</p>
                )}
                {suggestion.data.alternatives.length > 0 && (
                  <p className="mt-1 text-xs text-faint">
                    Also possible: {suggestion.data.alternatives.map((a) => `${a.categoryName} (${Math.round(a.probability * 100)}%)`).join(', ')}
                  </p>
                )}
              </div>
            ) : <p className="text-[13px] text-muted">Not sure yet. Add a few more details, or pick a category.</p>}
          </AssistCard>

          {(similar.data?.length ?? 0) > 0 && (
            <AssistCard title="Already reported?" icon={<Copy className="size-4" />} tone="warn">
              <p className="mb-2 text-[13px] text-muted">These open incidents look similar. If one matches, follow it instead of reporting again.</p>
              <ul className="space-y-1.5">
                {similar.data!.slice(0, 3).map((s) => (
                  <li key={s.number}>
                    <Link to={`/incidents/${s.number}`} className="block rounded-lg border border-line px-3 py-2 hover:bg-surface-2">
                      <span className="flex items-center justify-between gap-2 text-xs">
                        <span className="font-medium tabular text-accent-text">{s.number}</span>
                        <span className="tabular text-muted">{Math.round(s.score * 100)}% similar</span>
                      </span>
                      <span className="mt-0.5 block truncate text-[13px]">{s.title}</span>
                    </Link>
                  </li>
                ))}
              </ul>
            </AssistCard>
          )}
        </aside>
      </div>
    </div>
  )
}

function AssistCard({ title, icon, children, tone }: { title: string; icon: React.ReactNode; children: React.ReactNode; tone?: 'warn' }) {
  return (
    <section className={cn('rounded-xl border bg-surface p-4', tone === 'warn' ? 'border-p2/40' : 'border-line')}>
      <h2 className="mb-2.5 flex items-center gap-2 text-[13.5px] font-semibold">
        <span className={tone === 'warn' ? 'text-p2' : 'text-accent-text'}>{icon}</span>{title}
      </h2>
      {children}
    </section>
  )
}
