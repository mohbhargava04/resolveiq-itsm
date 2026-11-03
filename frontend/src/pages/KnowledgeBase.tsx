import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { BookOpen, Eye, Lightbulb, Plus, Search, ThumbsUp } from 'lucide-react'
import { api, qs } from '@/lib/api'
import { useAuth } from '@/lib/auth'
import { ago, cn } from '@/lib/format'
import { useDebounced } from '@/lib/useDebounced'
import type { ArticleSummary, CategoryView } from '@/lib/types'
import { Button, EmptyState, Input, PageHeader, Select, Skeleton } from '@/components/ui'

export default function KnowledgeBase() {
  const { is } = useAuth()
  const staff = is('TECHNICIAN')
  const [q, setQ] = useState('')
  const [category, setCategory] = useState('')
  const [status, setStatus] = useState('')
  const query = useDebounced(q, 300)
  const categories = useQuery({ queryKey: ['categories'], queryFn: () => api.get<CategoryView[]>('/categories') })
  const articles = useQuery({
    queryKey: ['kb', query, category, status],
    queryFn: () => api.get<ArticleSummary[]>(`/kb${qs({ q: query, category, status })}`),
    placeholderData: (prev) => prev,
  })

  return (
    <div>
      <PageHeader
        title="Knowledge base"
        description={staff ? 'Fixes written from real incidents. Published self-help articles are suggested to employees while they report.'
          : 'Quick fixes for common problems — often faster than waiting for a technician.'}
        actions={staff && <Link to="/kb/new"><Button variant="primary" icon={<Plus className="size-4" />}>New article</Button></Link>}
      />

      <div className="mb-6 flex flex-wrap gap-2">
        <div className="relative min-w-60 flex-1">
          <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-faint" aria-hidden />
          <Input aria-label="Search the knowledge base" value={q} onChange={(e) => setQ(e.target.value)}
            placeholder="Describe the problem, e.g. “vpn keeps disconnecting”" className="pl-9" />
        </div>
        <Select aria-label="Category" value={category} onChange={(e) => setCategory(e.target.value)} className="w-48">
          <option value="">All categories</option>
          {categories.data?.filter((c) => c.active).map((c) => <option key={c.code} value={c.code}>{c.name}</option>)}
        </Select>
        {staff && (
          <Select aria-label="Status" value={status} onChange={(e) => setStatus(e.target.value)} className="w-40">
            <option value="">Any status</option>
            <option value="PUBLISHED">Published</option>
            <option value="DRAFT">Drafts</option>
          </Select>
        )}
      </div>

      {articles.isLoading ? (
        <div className="grid gap-3 md:grid-cols-2">{[0, 1, 2, 3].map((i) => <Skeleton key={i} className="h-32" />)}</div>
      ) : !articles.data?.length ? (
        <EmptyState icon={<BookOpen className="size-8" />} title="No articles match" action={
          <Link to="/report"><Button>Report an incident instead</Button></Link>
        }>Try fewer or different words.</EmptyState>
      ) : (
        <ul className="grid gap-3 md:grid-cols-2">
          {articles.data.map((a) => <ArticleCard key={a.id} a={a} staff={staff} />)}
        </ul>
      )}
    </div>
  )
}

export function ArticleCard({ a, staff, compact }: { a: ArticleSummary; staff?: boolean; compact?: boolean }) {
  return (
    <li>
      <Link to={`/kb/${a.id}`} className={cn('group block h-full rounded-xl border border-line bg-surface transition-colors hover:border-line-strong', compact ? 'p-3.5' : 'p-4')}>
        <div className="flex items-start gap-3">
          <span className="mt-0.5 inline-flex size-7 shrink-0 items-center justify-center rounded-md bg-accent-soft text-accent-text"><Lightbulb className="size-4" aria-hidden /></span>
          <div className="min-w-0 flex-1">
            <p className="font-medium leading-snug group-hover:text-accent-text">{a.title}</p>
            {!compact && <p className="mt-1 line-clamp-2 text-[13px] text-muted">{a.excerpt}</p>}
            <div className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-faint">
              {a.service && <span>{a.service.name}</span>}
              <span className="inline-flex items-center gap-1"><Eye className="size-3" aria-hidden />{a.views}</span>
              <span className="inline-flex items-center gap-1"><ThumbsUp className="size-3" aria-hidden />{a.helpfulVotes}</span>
              {staff && a.status !== 'PUBLISHED' && <span className="rounded bg-p2-soft px-1.5 py-px font-medium text-p2">{a.status === 'DRAFT' ? 'Draft' : 'Archived'}</span>}
              {staff && a.audience === 'IT_STAFF' && <span className="rounded bg-surface-2 px-1.5 py-px font-medium text-muted">IT staff only</span>}
              {!compact && <span>Updated {ago(a.updatedAt)}</span>}
            </div>
          </div>
        </div>
      </Link>
    </li>
  )
}
