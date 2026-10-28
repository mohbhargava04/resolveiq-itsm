import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { cn, LEVEL_LABEL } from '@/lib/format'
import type { CorrelationCluster, DependencyGraph } from '@/lib/types'
import { PageHeader, Panel, Skeleton } from '@/components/ui'

const COL_W = 230
const NODE_W = 184
const NODE_H = 56
const ROW_H = 76
const PAD = 24

/**
 * The service catalogue drawn as a dependency graph: foundations on the left, the services people
 * use on the right. Open incidents light up their service; a probable common cause is outlined.
 */
export default function ServiceMap() {
  const navigate = useNavigate()
  const graph = useQuery({ queryKey: ['services', 'graph'], queryFn: () => api.get<DependencyGraph>('/services/graph'), refetchInterval: 30_000 })
  const correlations = useQuery({ queryKey: ['correlations'], queryFn: () => api.get<CorrelationCluster[]>('/intelligence/correlations'), refetchInterval: 30_000 })
  const [hover, setHover] = useState<string | null>(null)

  const layout = useMemo(() => (graph.data ? computeLayout(graph.data) : null), [graph.data])
  const roots = new Set(correlations.data?.map((c) => c.rootServiceCode))

  // services connected to the hovered one (both directions), to trace impact
  const related = useMemo(() => {
    if (!hover || !graph.data) return null
    const up = new Map<string, string[]>()
    const down = new Map<string, string[]>()
    graph.data.edges.forEach((e) => {
      up.set(e.from, [...(up.get(e.from) ?? []), e.to])
      down.set(e.to, [...(down.get(e.to) ?? []), e.from])
    })
    const walk = (start: string, m: Map<string, string[]>) => {
      const seen = new Set<string>()
      const stack = [start]
      while (stack.length) {
        const c = stack.pop()!
        for (const n of m.get(c) ?? []) if (!seen.has(n)) { seen.add(n); stack.push(n) }
      }
      return seen
    }
    return new Set([hover, ...walk(hover, up), ...walk(hover, down)])
  }, [hover, graph.data])

  return (
    <div>
      <PageHeader title="Service map" description="Which services depend on which. Hover a service to trace everything it relies on and everything that relies on it." />

      <Panel bodyClassName="p-0">
        <div className="flex flex-wrap items-center gap-x-5 gap-y-2 border-b border-line px-5 py-3 text-xs text-muted">
          <span className="flex items-center gap-1.5"><span className="size-2.5 rounded-sm border border-line-strong bg-surface" />Healthy</span>
          <span className="flex items-center gap-1.5"><span className="size-2.5 rounded-sm bg-p2" />Open incidents</span>
          <span className="flex items-center gap-1.5"><span className="size-2.5 rounded-sm border-2 border-p1" />Probable common cause</span>
          <span className="ml-auto">Foundations on the left, the services people use on the right</span>
        </div>
        {!layout ? <Skeleton className="m-5 h-96" /> : (
          <div className="overflow-x-auto">
            <svg width={layout.width} height={layout.height} className="block" role="img" aria-label="Service dependency graph">
              <defs>
                <marker id="arrow" viewBox="0 0 8 8" refX="7" refY="4" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
                  <path d="M0,0 L8,4 L0,8 z" fill="var(--line-strong)" />
                </marker>
              </defs>
              {graph.data!.edges.map((e) => {
                const a = layout.pos.get(e.from)!
                const b = layout.pos.get(e.to)!
                // edge drawn from the dependency (left) to the dependent (right)
                const x1 = b.x + NODE_W, y1 = b.y + NODE_H / 2, x2 = a.x, y2 = a.y + NODE_H / 2
                const mid = (x1 + x2) / 2
                const lit = related ? related.has(e.from) && related.has(e.to) : false
                return (
                  <path key={`${e.from}-${e.to}`} d={`M${x1},${y1} C${mid},${y1} ${mid},${y2} ${x2 - 2},${y2}`}
                    fill="none" stroke={lit ? 'var(--accent)' : 'var(--line-strong)'} strokeWidth={lit ? 1.75 : 1}
                    opacity={related && !lit ? 0.25 : 1} markerEnd="url(#arrow)" />
                )
              })}
              {graph.data!.nodes.map((n) => {
                const p = layout.pos.get(n.code)!
                const open = n.openIncidents > 0
                const root = roots.has(n.code)
                const dim = related && !related.has(n.code)
                return (
                  <g key={n.code} transform={`translate(${p.x},${p.y})`} opacity={dim ? 0.3 : 1}
                    onMouseEnter={() => setHover(n.code)} onMouseLeave={() => setHover(null)}
                    onClick={() => navigate(`/incidents?service=${n.code}&status=OPEN`)}
                    className="cursor-pointer" role="link" aria-label={`${n.name}, ${n.openIncidents} open incidents`}>
                    <rect width={NODE_W} height={NODE_H} rx={10}
                      fill={open ? 'var(--p2-soft)' : 'var(--surface)'}
                      stroke={root ? 'var(--p1)' : open ? 'var(--p2)' : 'var(--line-strong)'} strokeWidth={root ? 2 : 1} />
                    <text x={12} y={23} className="fill-[var(--ink)] text-[12.5px] font-semibold">{truncate(n.name, 22)}</text>
                    <text x={12} y={41} className="fill-[var(--muted)] text-[11px]">{LEVEL_LABEL[n.criticality]} criticality</text>
                    {open && (
                      <g transform={`translate(${NODE_W - 30},${NODE_H / 2 - 11})`}>
                        <rect width={22} height={22} rx={11} fill="var(--p2)" />
                        <text x={11} y={15} textAnchor="middle" className="fill-white text-[11px] font-semibold">{n.openIncidents}</text>
                      </g>
                    )}
                  </g>
                )
              })}
            </svg>
          </div>
        )}
      </Panel>

      {correlations.data && correlations.data.length > 0 && (
        <p className={cn('mt-3 text-[13px] text-muted')}>
          {correlations.data.map((c) => c.summary).join(' ')}
        </p>
      )}
    </div>
  )
}

function truncate(s: string, n: number) {
  return s.length > n ? `${s.slice(0, n - 1)}…` : s
}

/** Layered layout: a service's column is one more than the deepest service it depends on. */
function computeLayout(g: DependencyGraph) {
  const deps = new Map<string, string[]>()
  g.nodes.forEach((n) => deps.set(n.code, []))
  g.edges.forEach((e) => deps.get(e.from)?.push(e.to))
  const layer = new Map<string, number>()
  const depth = (c: string, guard = 0): number => {
    if (layer.has(c)) return layer.get(c)!
    if (guard > 50) return 0
    const d = deps.get(c) ?? []
    const v = d.length ? Math.max(...d.map((x) => depth(x, guard + 1))) + 1 : 0
    layer.set(c, v)
    return v
  }
  g.nodes.forEach((n) => depth(n.code))
  const columns = new Map<number, string[]>()
  g.nodes
    .slice()
    .sort((a, b) => b.openIncidents - a.openIncidents || a.name.localeCompare(b.name))
    .forEach((n) => {
      const l = layer.get(n.code)!
      columns.set(l, [...(columns.get(l) ?? []), n.code])
    })
  const maxRows = Math.max(...[...columns.values()].map((c) => c.length))
  const height = PAD * 2 + maxRows * ROW_H - (ROW_H - NODE_H)
  const pos = new Map<string, { x: number; y: number }>()
  columns.forEach((codes, l) => {
    const colHeight = codes.length * ROW_H - (ROW_H - NODE_H)
    const top = (height - colHeight) / 2
    codes.forEach((c, i) => pos.set(c, { x: PAD + l * COL_W, y: top + i * ROW_H }))
  })
  const width = PAD * 2 + (Math.max(...columns.keys()) + 1) * COL_W - (COL_W - NODE_W)
  return { pos, width, height }
}
