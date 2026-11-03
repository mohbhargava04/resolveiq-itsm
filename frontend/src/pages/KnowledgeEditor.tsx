import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { useMutation, useQuery } from '@tanstack/react-query'
import { toast } from 'sonner'
import { ArrowLeft } from 'lucide-react'
import { api, ApiError } from '@/lib/api'
import type { ArticleView, CategoryView, ServiceView } from '@/lib/types'
import { Button, ErrorNote, Field, Input, PageHeader, Panel, Segmented, Select, Skeleton, Switch, Textarea } from '@/components/ui'

interface Form {
  title: string; symptoms: string; solution: string; categoryCode: string; serviceCode: string; tags: string
  audience: 'EVERYONE' | 'IT_STAFF'; publish: boolean
}

const EMPTY: Form = { title: '', symptoms: '', solution: '', categoryCode: '', serviceCode: '', tags: '', audience: 'EVERYONE', publish: true }

/** Write or edit an article; ?fromIncident=INC-1042 pre-fills it from a resolved incident. */
export default function KnowledgeEditor() {
  const { id } = useParams()
  const [params] = useSearchParams()
  const fromIncident = params.get('fromIncident')
  const navigate = useNavigate()
  const [form, setForm] = useState<Form>(EMPTY)
  const [ready, setReady] = useState(!id && !fromIncident)
  const services = useQuery({ queryKey: ['services'], queryFn: () => api.get<ServiceView[]>('/services') })
  const categories = useQuery({ queryKey: ['categories'], queryFn: () => api.get<CategoryView[]>('/categories') })

  useEffect(() => {
    if (id) {
      api.get<ArticleView>(`/kb/${id}`).then((a) => {
        setForm({ title: a.title, symptoms: a.symptoms, solution: a.solution, categoryCode: a.category?.code ?? '',
          serviceCode: a.service?.code ?? '', tags: a.tags.join(', '), audience: a.audience, publish: a.status === 'PUBLISHED' })
        setReady(true)
      })
    } else if (fromIncident) {
      api.get<Partial<Form>>(`/kb/draft-from-incident/${fromIncident}`).then((d) => {
        setForm({ ...EMPTY, ...d, tags: d.tags ?? '', categoryCode: d.categoryCode ?? '', serviceCode: d.serviceCode ?? '', publish: false })
        setReady(true)
      }).catch(() => setReady(true))
    }
  }, [id, fromIncident])

  const save = useMutation({
    mutationFn: () => {
      const body = { ...form, categoryCode: form.categoryCode || null, serviceCode: form.serviceCode || null, sourceIncident: fromIncident }
      return id ? api.put<ArticleView>(`/kb/${id}`, body) : api.post<ArticleView>('/kb', body)
    },
    onSuccess: (a) => { toast.success(a.status === 'PUBLISHED' ? 'Article published' : 'Draft saved'); navigate(`/kb/${a.id}`) },
  })
  const fieldErrors = save.error instanceof ApiError ? save.error.fieldErrors ?? {} : {}
  const set = (k: keyof Form) => (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => setForm({ ...form, [k]: e.target.value })

  return (
    <div className="max-w-3xl">
      <Link to={id ? `/kb/${id}` : '/kb'} className="mb-5 inline-flex items-center gap-1.5 text-[13px] text-muted hover:text-ink"><ArrowLeft className="size-4" /> Back</Link>
      <PageHeader title={id ? 'Edit article' : 'New article'}
        description={fromIncident ? `Pre-filled from ${fromIncident}. Rewrite it in plain words for the person who will hit this problem next.` : 'Write for the person who has the problem, not for IT.'} />
      {!ready ? <Skeleton className="h-96" /> : (
        <Panel>
          <form onSubmit={(e: FormEvent) => { e.preventDefault(); save.mutate() }} className="space-y-5">
            <Field label="Title" htmlFor="kb-title" hint="Phrase it the way someone would describe the problem" error={fieldErrors.title}>
              <Input id="kb-title" required maxLength={160} value={form.title} onChange={set('title')} placeholder="VPN keeps disconnecting when working from home" />
            </Field>
            <Field label="Symptoms" htmlFor="kb-symptoms" hint="What the person sees. This text is matched against new reports." error={fieldErrors.symptoms}>
              <Textarea id="kb-symptoms" required rows={3} maxLength={2000} value={form.symptoms} onChange={set('symptoms')} />
            </Field>
            <Field label="Solution" htmlFor="kb-solution" hint="Numbered steps work best" error={fieldErrors.solution}>
              <Textarea id="kb-solution" required rows={8} maxLength={6000} value={form.solution} onChange={set('solution')} />
            </Field>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field label="Service" htmlFor="kb-service">
                <Select id="kb-service" value={form.serviceCode} onChange={set('serviceCode')}>
                  <option value="">Any service</option>
                  {services.data?.filter((s) => s.active).map((s) => <option key={s.code} value={s.code}>{s.name}</option>)}
                </Select>
              </Field>
              <Field label="Category" htmlFor="kb-category">
                <Select id="kb-category" value={form.categoryCode} onChange={set('categoryCode')}>
                  <option value="">None</option>
                  {categories.data?.filter((c) => c.active).map((c) => <option key={c.code} value={c.code}>{c.name}</option>)}
                </Select>
              </Field>
            </div>
            <Field label="Tags" htmlFor="kb-tags" hint="Comma separated; extra words people might search for">
              <Input id="kb-tags" value={form.tags} onChange={set('tags')} placeholder="vpn, remote, home" />
            </Field>
            <Field label="Who can see it">
              <Segmented name="Audience" value={form.audience} onChange={(v) => setForm({ ...form, audience: v })}
                options={[{ value: 'EVERYONE', label: 'Everyone (self-help)' }, { value: 'IT_STAFF', label: 'IT staff only (runbook)' }]} />
            </Field>
            <div className="flex items-center justify-between rounded-lg border border-line px-4 py-3">
              <div>
                <p className="text-[13.5px] font-medium">Publish now</p>
                <p className="text-xs text-muted">Off saves a draft only IT staff can see.</p>
              </div>
              <Switch checked={form.publish} onChange={(v) => setForm({ ...form, publish: v })} label="Publish now" />
            </div>
            {save.error && !Object.keys(fieldErrors).length ? <ErrorNote error={save.error} /> : null}
            <div className="flex justify-end gap-2">
              <Button type="button" variant="ghost" onClick={() => navigate(-1)}>Cancel</Button>
              <Button type="submit" variant="primary" loading={save.isPending}>{form.publish ? 'Publish' : 'Save draft'}</Button>
            </div>
          </form>
        </Panel>
      )}
    </div>
  )
}
