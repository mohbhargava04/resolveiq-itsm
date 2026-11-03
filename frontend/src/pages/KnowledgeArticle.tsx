import { Link, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { ArrowLeft, CheckCircle2, Pencil, Send, ThumbsDown, ThumbsUp, Archive } from 'lucide-react'
import { api } from '@/lib/api'
import { useAuth } from '@/lib/auth'
import { ago, cn } from '@/lib/format'
import type { ArticleView } from '@/lib/types'
import { Button, EmptyState, Panel, Skeleton } from '@/components/ui'
import { ArticleCard } from './KnowledgeBase'

export default function KnowledgeArticle() {
  const { id } = useParams()
  const { is } = useAuth()
  const staff = is('TECHNICIAN')
  const qc = useQueryClient()
  const article = useQuery({ queryKey: ['kb-article', id], queryFn: () => api.get<ArticleView>(`/kb/${id}`) })
  const set = (a: ArticleView) => qc.setQueryData(['kb-article', id], (old: ArticleView | undefined) => ({ ...a, related: old?.related ?? a.related }))
  const vote = useMutation({ mutationFn: (helpful: boolean) => api.post<ArticleView>(`/kb/${id}/vote`, { helpful }), onSuccess: set })
  const solved = useMutation({
    mutationFn: () => api.post(`/kb/${id}/solved-my-problem`),
    onSuccess: () => toast.success('Great — glad it is fixed. No incident needed.'),
  })
  const publish = useMutation({ mutationFn: () => api.post<ArticleView>(`/kb/${id}/publish`), onSuccess: (a) => { set(a); toast.success('Published') } })
  const archive = useMutation({ mutationFn: () => api.post<ArticleView>(`/kb/${id}/archive`), onSuccess: (a) => { set(a); toast.success('Archived') } })

  if (article.isLoading) return <div className="max-w-3xl space-y-3"><Skeleton className="h-8 w-2/3" /><Skeleton className="h-40" /></div>
  if (!article.data) return <EmptyState heading="h1" title="Article not found"><Link to="/kb" className="text-accent-text hover:underline">Back to the knowledge base</Link></EmptyState>
  const a = article.data

  return (
    <div className="grid gap-8 lg:grid-cols-[minmax(0,1fr)_260px]">
      <article className="min-w-0">
        <Link to="/kb" className="mb-5 inline-flex items-center gap-1.5 text-[13px] text-muted hover:text-ink"><ArrowLeft className="size-4" /> Knowledge base</Link>
        <div className="flex flex-wrap items-center gap-2 text-xs text-muted">
          {a.service && <span className="rounded-md bg-surface-2 px-2 py-0.5 font-medium text-ink-2">{a.service.name}</span>}
          {a.category && <span>{a.category.name}</span>}
          {a.status !== 'PUBLISHED' && <span className="rounded bg-p2-soft px-1.5 py-px font-medium text-p2">{a.status === 'DRAFT' ? 'Draft' : 'Archived'}</span>}
          {a.audience === 'IT_STAFF' && <span className="rounded bg-surface-2 px-1.5 py-px font-medium">IT staff only</span>}
        </div>
        <h1 className="mt-2 text-[26px] font-semibold leading-tight tracking-tight text-balance">{a.title}</h1>
        <p className="mt-2 text-[13px] text-muted">
          By {a.author} · updated {ago(a.updatedAt)} · {a.views} views
          {a.sourceIncident && staff && <> · written from <Link to={`/incidents/${a.sourceIncident}`} className="text-accent-text hover:underline">{a.sourceIncident}</Link></>}
        </p>

        <section className="mt-7">
          <h2 className="text-[13px] font-semibold uppercase tracking-wide text-faint">When this happens</h2>
          <p className="mt-2 whitespace-pre-line leading-relaxed text-ink-2">{a.symptoms}</p>
        </section>
        <section className="mt-7 rounded-xl border border-line bg-surface p-5">
          <h2 className="text-[13px] font-semibold uppercase tracking-wide text-faint">How to fix it</h2>
          <div className="mt-3 whitespace-pre-line leading-relaxed">{a.solution}</div>
        </section>

        {a.status === 'PUBLISHED' && (
          <div className="mt-6 flex flex-wrap items-center gap-3 rounded-xl border border-line bg-surface px-5 py-4">
            <p className="mr-auto text-[13.5px] font-medium">Did this solve your problem?</p>
            <Button variant="primary" size="sm" icon={<CheckCircle2 className="size-4" />} loading={solved.isPending}
              disabled={solved.isSuccess} onClick={() => solved.mutate()}>{solved.isSuccess ? 'Glad it helped' : 'Yes, it is fixed'}</Button>
            <Link to="/report"><Button size="sm">No, report an incident</Button></Link>
          </div>
        )}

        {a.status === 'PUBLISHED' && (
          <div className="mt-4 flex items-center gap-2 text-[13px] text-muted">
            Was this article helpful?
            <button aria-pressed={a.myVote === true} onClick={() => vote.mutate(true)} className={cn('inline-flex items-center gap-1 rounded-md px-2 py-1 hover:bg-surface-2 cursor-pointer', a.myVote === true && 'bg-ok-soft text-ok')}>
              <ThumbsUp className="size-3.5" /> {a.helpfulVotes}
            </button>
            <button aria-pressed={a.myVote === false} onClick={() => vote.mutate(false)} className={cn('inline-flex items-center gap-1 rounded-md px-2 py-1 hover:bg-surface-2 cursor-pointer', a.myVote === false && 'bg-p1-soft text-p1')}>
              <ThumbsDown className="size-3.5" /> {a.unhelpfulVotes}
            </button>
          </div>
        )}
      </article>

      <aside aria-label="About this article" className="space-y-5">
        {staff && (
          <Panel title="Manage">
            <div className="flex flex-col gap-2">
              <Link to={`/kb/${a.id}/edit`}><Button className="w-full" icon={<Pencil className="size-4" />}>Edit</Button></Link>
              {a.status !== 'PUBLISHED' && <Button variant="primary" icon={<Send className="size-4" />} loading={publish.isPending} onClick={() => publish.mutate()}>Publish</Button>}
              {is('TEAM_LEAD') && a.status === 'PUBLISHED' && <Button variant="ghost" icon={<Archive className="size-4" />} loading={archive.isPending} onClick={() => archive.mutate()}>Archive</Button>}
            </div>
            <dl className="mt-4 grid grid-cols-2 gap-3 text-center">
              <div className="rounded-lg bg-surface-2 py-2"><dt className="text-[11px] text-muted">Problems solved</dt><dd className="text-lg font-semibold tabular">{a.deflections}</dd></div>
              <div className="rounded-lg bg-surface-2 py-2"><dt className="text-[11px] text-muted">Helpful</dt><dd className="text-lg font-semibold tabular">{a.helpfulVotes}</dd></div>
            </dl>
          </Panel>
        )}
        {a.tags.length > 0 && (
          <div>
            <h2 className="mb-2 text-xs font-medium text-faint">Tags</h2>
            <div className="flex flex-wrap gap-1.5">{a.tags.map((t) => <span key={t} className="rounded-md bg-surface-2 px-2 py-0.5 text-xs text-muted">{t}</span>)}</div>
          </div>
        )}
        {a.related.length > 0 && (
          <div>
            <h2 className="mb-2 text-xs font-medium text-faint">Related articles</h2>
            <ul className="space-y-2">{a.related.map((r) => <ArticleCard key={r.id} a={r} compact />)}</ul>
          </div>
        )}
      </aside>
    </div>
  )
}
