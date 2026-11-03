import { useMemo, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { CheckCircle2, Copy, FileText, Lightbulb, Paperclip, Siren, Sparkles, Timer, X } from 'lucide-react'
import { api, ApiError } from '@/lib/api'
import { cn, duration, LEVEL_LABEL, PRIORITY_LABEL, STATUS_LABEL } from '@/lib/format'
import { useDebounced } from '@/lib/useDebounced'
import type {
  ArticleSummary, ArticleView, CategorySuggestion, CategoryView, Level, MajorBanner, PriorityPreview, ReportResponse, ServiceView, SimilarIncident,
} from '@/lib/types'
import { Button, Dialog, ErrorNote, Field, Input, PageHeader, Segmented, Select, Skeleton, Textarea } from '@/components/ui'
import { ACCEPT } from '@/components/incident-collab'
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
  const [files, setFiles] = useState<File[]>([])
  const [article, setArticle] = useState<number | null>(null)
  const addFiles = (list: FileList | File[]) => setFiles((f) => [...f, ...[...list]].slice(0, 10))

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

  // self-help: knowledge-base articles that match what is being typed
  const articles = useQuery({
    queryKey: ['assist', 'kb', serviceCode, text],
    queryFn: () => api.post<ArticleSummary[]>('/kb/suggest', { text, serviceCode: serviceCode || undefined }),
    enabled: text.length >= 12,
    placeholderData: (p) => p,
  })

  // a major incident already covers this service: join it instead of reporting again
  const majors = useQuery({ queryKey: ['major-incidents'], queryFn: () => api.get<MajorBanner[]>('/major-incidents') })
  const majorHere = majors.data?.find((m) => m.service.code === serviceCode)
  const affected = useMutation({
    mutationFn: (n: string) => api.post<{ number: string }>(`/major-incidents/${n}/affected`, { details: description || undefined }),
    onSuccess: (r) => { qc.invalidateQueries({ queryKey: ['major-incidents'] }); toast.success('You will get every update on this outage'); navigate(`/incidents/${r.number}`) },
  })

  const report = useMutation({
    mutationFn: async (acknowledgeDuplicates: boolean) => {
      const res = await api.post<ReportResponse>('/incidents', {
        title, description, serviceCode, categoryCode: categoryCode || undefined, impact, urgency,
        affectedUsers: users, acknowledgeDuplicates,
      })
      // attach files after the incident exists; a rejected file does not undo the report
      const failed: string[] = []
      for (const f of files) {
        try { await api.upload(`/incidents/${res.incident.number}/attachments`, f) } catch (e) { failed.push(`${f.name}: ${e instanceof Error ? e.message : 'failed'}`) }
      }
      if (failed.length) toast.error('Some files were not attached', { description: failed.join('\n') })
      return res
    },
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
              onPaste={(e) => { const imgs = [...e.clipboardData.files].filter((f) => f.type.startsWith('image/')); if (imgs.length) { e.preventDefault(); addFiles(imgs.map((f) => new File([f], `screenshot-${Date.now()}.png`, { type: f.type }))) } }}
              placeholder="The payment page spins for about a minute and then shows 'transaction failed'. It started around 10:15." />
          </Field>

          <div>
            <div className="flex flex-wrap items-center gap-2">
              <label className="inline-flex h-8 cursor-pointer items-center gap-1.5 rounded-md border border-line-strong px-2.5 text-[13px] font-medium text-ink-2 hover:bg-surface-2 focus-within:ring-3 focus-within:ring-accent/20">
                <Paperclip className="size-3.5" aria-hidden /> Attach screenshots or logs
                <input type="file" multiple accept={ACCEPT} className="sr-only" onChange={(e) => { if (e.target.files) addFiles(e.target.files); e.target.value = '' }} />
              </label>
              <span className="text-xs text-faint">or paste a screenshot into the details · up to 5 MB each</span>
            </div>
            {files.length > 0 && (
              <ul className="mt-2 flex flex-wrap gap-2">
                {files.map((f, idx) => (
                  <li key={idx} className="inline-flex max-w-full items-center gap-1.5 rounded-md bg-surface-2 py-1 pl-2 pr-1 text-xs">
                    <FileText className="size-3.5 shrink-0 text-muted" aria-hidden />
                    <span className="truncate">{f.name}</span>
                    <button type="button" aria-label={`Remove ${f.name}`} onClick={() => setFiles(files.filter((_, j) => j !== idx))} className="rounded p-0.5 text-faint hover:text-ink cursor-pointer"><X className="size-3" /></button>
                  </li>
                ))}
              </ul>
            )}
          </div>

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

        <aside aria-label="Suggestions" className="space-y-4 lg:sticky lg:top-20 lg:self-start" aria-live="polite">
          {majorHere && (
            <AssistCard title="Known outage" icon={<Siren className="size-4" />} tone="danger">
              <p className="text-[13px]"><span className="font-medium">{majorHere.title}</span></p>
              <p className="mt-1 text-xs text-muted">{majorHere.stageLabel}{majorHere.latestUpdate ? ` — ${majorHere.latestUpdate.message}` : ''}</p>
              {majorHere.affectsMe ? (
                <p className="mt-3 text-xs font-medium text-muted">You're already receiving its updates.</p>
              ) : (
                <Button size="sm" className="mt-3" variant="primary" loading={affected.isPending} onClick={() => affected.mutate(majorHere.number)}>I'm affected by this — keep me updated</Button>
              )}
            </AssistCard>
          )}

          {(articles.data?.length ?? 0) > 0 && (
            <AssistCard title="Fix it yourself?" icon={<Lightbulb className="size-4" />}>
              <p className="mb-2 text-[13px] text-muted">These articles solved similar problems for others.</p>
              <ul className="space-y-1.5">
                {articles.data!.map((a) => (
                  <li key={a.id}>
                    <button type="button" onClick={() => setArticle(a.id)} className="block w-full rounded-lg border border-line px-3 py-2 text-left hover:bg-surface-2 cursor-pointer">
                      <span className="block text-[13px] font-medium">{a.title}</span>
                      <span className="mt-0.5 block text-xs text-muted">Solved {a.deflections} problem{a.deflections === 1 ? '' : 's'} · {a.helpfulVotes} found it helpful</span>
                    </button>
                  </li>
                ))}
              </ul>
            </AssistCard>
          )}

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
      {article !== null && <ArticlePreview id={article} onClose={() => setArticle(null)} onSolved={() => { toast.success('Glad it is fixed — no incident needed.'); navigate('/') }} />}
    </div>
  )
}

/** Reads an article without leaving the half-written report; "this fixed it" counts a deflection. */
function ArticlePreview({ id, onClose, onSolved }: { id: number; onClose: () => void; onSolved: () => void }) {
  const a = useQuery({ queryKey: ['kb-article', String(id)], queryFn: () => api.get<ArticleView>(`/kb/${id}`) })
  const solved = useMutation({ mutationFn: () => api.post(`/kb/${id}/solved-my-problem`), onSuccess: onSolved })
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()} title={a.data?.title ?? 'Article'} width={620}
      footer={<><Button variant="ghost" onClick={onClose}>Not quite — continue my report</Button>
        <Button variant="primary" icon={<CheckCircle2 className="size-4" />} loading={solved.isPending} onClick={() => solved.mutate()}>This fixed it</Button></>}>
      {!a.data ? <Skeleton className="h-40" /> : (
        <div className="space-y-4 text-[13.5px]">
          <div>
            <h3 className="text-xs font-semibold uppercase tracking-wide text-faint">When this happens</h3>
            <p className="mt-1 whitespace-pre-line text-ink-2">{a.data.symptoms}</p>
          </div>
          <div className="rounded-lg bg-surface-2 p-4">
            <h3 className="text-xs font-semibold uppercase tracking-wide text-faint">How to fix it</h3>
            <p className="mt-1 whitespace-pre-line leading-relaxed">{a.data.solution}</p>
          </div>
        </div>
      )}
    </Dialog>
  )
}

function AssistCard({ title, icon, children, tone }: { title: string; icon: React.ReactNode; children: React.ReactNode; tone?: 'warn' | 'danger' }) {
  return (
    <section className={cn('rounded-xl border bg-surface p-4', tone === 'warn' ? 'border-p2/40' : tone === 'danger' ? 'border-p1/40 bg-p1-soft/40' : 'border-line')}>
      <h2 className="mb-2.5 flex items-center gap-2 text-[13.5px] font-semibold">
        <span className={tone === 'warn' ? 'text-p2' : tone === 'danger' ? 'text-p1' : 'text-accent-text'}>{icon}</span>{title}
      </h2>
      {children}
    </section>
  )
}
