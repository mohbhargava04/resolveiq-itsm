import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { CheckCircle2, Copy, Link2, UserPlus, Wrench, X, XCircle } from 'lucide-react'
import { api } from '@/lib/api'
import type { BulkResult, MajorBanner, User } from '@/lib/types'
import { Button, Dialog, ErrorNote, Field, Input, Select, Textarea } from './ui'

type Action = 'ASSIGN' | 'LINK_TO_MAJOR' | 'MARK_DUPLICATE' | 'RESOLVE' | 'CLOSE'

/**
 * Bar shown when incidents are selected. Each incident is processed on its own on the server, so the
 * result can be a mix of successes and refusals (e.g. a closed incident cannot be assigned).
 */
export function BulkBar({ selected, onClear }: { selected: string[]; onClear: () => void }) {
  const [action, setAction] = useState<Action | null>(null)
  const [results, setResults] = useState<BulkResult[] | null>(null)
  const qc = useQueryClient()
  const run = useMutation({
    mutationFn: (v: { action: Action; params: Record<string, string> }) =>
      api.post<BulkResult[]>('/incidents/bulk', { numbers: selected, action: v.action, params: v.params }),
    onSuccess: (r) => {
      setAction(null)
      setResults(r)
      qc.invalidateQueries({ queryKey: ['incidents'] })
      qc.invalidateQueries({ queryKey: ['dashboard'] })
      const ok = r.filter((x) => x.ok).length
      if (ok === r.length) { toast.success(`${ok} incident${ok === 1 ? '' : 's'} updated`); setResults(null); onClear() }
    },
  })

  return (
    <>
      <div role="region" aria-label="Bulk actions"
        className="sticky bottom-4 z-20 mx-auto mt-4 flex w-fit max-w-full flex-wrap items-center gap-2 rounded-xl border border-line bg-surface px-3 py-2 shadow-pop animate-slide-in">
        <span className="px-1 text-[13px] font-medium tabular">{selected.length} selected</span>
        <span className="h-5 w-px bg-line" />
        <Button size="sm" icon={<UserPlus className="size-3.5" />} onClick={() => setAction('ASSIGN')}>Assign</Button>
        <Button size="sm" icon={<Link2 className="size-3.5" />} onClick={() => setAction('LINK_TO_MAJOR')}>Link to major</Button>
        <Button size="sm" icon={<Copy className="size-3.5" />} onClick={() => setAction('MARK_DUPLICATE')}>Duplicate of…</Button>
        <Button size="sm" icon={<Wrench className="size-3.5" />} onClick={() => setAction('RESOLVE')}>Resolve</Button>
        <Button size="sm" onClick={() => setAction('CLOSE')}>Close</Button>
        <button onClick={onClear} aria-label="Clear selection" className="rounded-md p-1.5 text-muted hover:bg-surface-2 hover:text-ink cursor-pointer"><X className="size-4" /></button>
      </div>
      {action && <ActionDialog action={action} count={selected.length} busy={run.isPending} error={run.error}
        onClose={() => setAction(null)} onRun={(params) => run.mutate({ action, params })} />}
      {results && (
        <Dialog open onOpenChange={(o) => { if (!o) { setResults(null); onClear() } }} title="Bulk action results"
          description={`${results.filter((r) => r.ok).length} of ${results.length} updated`}
          footer={<Button variant="primary" onClick={() => { setResults(null); onClear() }}>Done</Button>}>
          <ul className="space-y-2">
            {results.map((r) => (
              <li key={r.number} className="flex items-start gap-2 text-[13px]">
                {r.ok ? <CheckCircle2 className="mt-0.5 size-4 shrink-0 text-ok" /> : <XCircle className="mt-0.5 size-4 shrink-0 text-p1" />}
                <Link to={`/incidents/${r.number}`} className="tabular font-medium text-accent-text hover:underline">{r.number}</Link>
                <span className="text-muted">{r.message}</span>
              </li>
            ))}
          </ul>
        </Dialog>
      )}
    </>
  )
}

function ActionDialog({ action, count, busy, error, onClose, onRun }: {
  action: Action; count: number; busy: boolean; error: unknown; onClose: () => void; onRun: (params: Record<string, string>) => void
}) {
  const [p, setP] = useState<Record<string, string>>(action === 'ASSIGN' ? { strategy: 'LOAD' } : {})
  const technicians = useQuery({ queryKey: ['technicians'], queryFn: () => api.get<User[]>('/technicians'), enabled: action === 'ASSIGN' })
  const majors = useQuery({ queryKey: ['major-incidents'], queryFn: () => api.get<MajorBanner[]>('/major-incidents'), enabled: action === 'LINK_TO_MAJOR' })
  const set = (k: string) => (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => setP({ ...p, [k]: e.target.value })
  const titles: Record<Action, string> = {
    ASSIGN: `Assign ${count} incidents`, LINK_TO_MAJOR: `Link ${count} incidents to a major incident`,
    MARK_DUPLICATE: `Close ${count} incidents as duplicates`, RESOLVE: `Resolve ${count} incidents`, CLOSE: `Close ${count} incidents`,
  }
  const valid = action === 'ASSIGN' ? !!(p.technicianId || p.strategy) : action === 'LINK_TO_MAJOR' ? !!p.majorNumber
    : action === 'MARK_DUPLICATE' ? !!p.parentNumber?.trim() : action === 'RESOLVE' ? !!p.summary?.trim() : true
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()} title={titles[action]}
      footer={<><Button variant="ghost" onClick={onClose}>Cancel</Button><Button variant="primary" loading={busy} disabled={!valid} onClick={() => onRun(p)}>Apply to {count}</Button></>}>
      <div className="space-y-4">
        {action === 'ASSIGN' && (
          <>
            <Field label="Technician" htmlFor="b-tech" hint="Leave empty to pick per incident with a strategy">
              <Select id="b-tech" value={p.technicianId ?? ''} onChange={set('technicianId')}>
                <option value="">Choose automatically</option>
                {technicians.data?.filter((t) => t.technician?.available).map((t) => <option key={t.id} value={t.id}>{t.fullName}</option>)}
              </Select>
            </Field>
            {!p.technicianId && (
              <Field label="Strategy" htmlFor="b-strat">
                <Select id="b-strat" value={p.strategy ?? 'LOAD'} onChange={set('strategy')}>
                  <option value="LOAD">Least busy (load-based)</option>
                  <option value="SMART">Smart recommendation</option>
                  <option value="SKILL">Best skill match</option>
                  <option value="ROUND_ROBIN">Round robin</option>
                  <option value="EXPERIENCE">Most experienced</option>
                </Select>
              </Field>
            )}
          </>
        )}
        {action === 'LINK_TO_MAJOR' && (
          <Field label="Major incident" htmlFor="b-major">
            <Select id="b-major" value={p.majorNumber ?? ''} onChange={set('majorNumber')}>
              <option value="" disabled>{majors.data?.length ? 'Choose' : 'No open major incidents'}</option>
              {majors.data?.map((m) => <option key={m.number} value={m.number}>{m.number} · {m.title}</option>)}
            </Select>
          </Field>
        )}
        {action === 'MARK_DUPLICATE' && (
          <Field label="Original incident number" htmlFor="b-parent">
            <Input id="b-parent" value={p.parentNumber ?? ''} onChange={set('parentNumber')} placeholder="INC-1037" className="tabular" />
          </Field>
        )}
        {action === 'RESOLVE' && (
          <>
            <Field label="What was done" htmlFor="b-sum"><Textarea id="b-sum" rows={3} value={p.summary ?? ''} onChange={set('summary')} /></Field>
            <Field label="Root cause" htmlFor="b-root" hint="Optional"><Input id="b-root" value={p.rootCause ?? ''} onChange={set('rootCause')} /></Field>
            <p className="text-xs text-muted">Only incidents that are in progress can be resolved; the others will be listed as skipped.</p>
          </>
        )}
        {action === 'CLOSE' && (
          <Field label="Closing note" htmlFor="b-note" hint="Optional"><Input id="b-note" value={p.note ?? ''} onChange={set('note')} /></Field>
        )}
        {error ? <ErrorNote error={error} /> : null}
      </div>
    </Dialog>
  )
}
