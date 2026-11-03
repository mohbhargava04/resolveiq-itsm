import { useEffect, useRef, useState, type ClipboardEvent, type DragEvent } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Download, FileText, ImageIcon, Link2, Lock, Megaphone, Paperclip, Send, Siren, Sparkles, Trash2, Upload } from 'lucide-react'
import { api, downloadFile, fetchBlob } from '@/lib/api'
import { useUser } from '@/lib/auth'
import { ago, cn, when } from '@/lib/format'
import type { AttachmentView, IncidentDetail as Incident, MajorBanner, MajorStage, NoteView, SimilarIncident } from '@/lib/types'
import { Avatar, Button, Dialog, ErrorNote, Field, Segmented, Skeleton, Textarea } from './ui'
import { StatusBadge } from './incident-bits'

// ---------------------------------------------------------------------------------------------
// Conversation: a chat-style thread between the reporter and IT, with internal notes for staff
// ---------------------------------------------------------------------------------------------

const NOTE_LABEL: Record<NoteView['type'], string> = {
  INVESTIGATION: 'Work note', COMMENT: 'Message', INFO_REQUEST: 'Question for the reporter', INFO_RESPONSE: 'Answer',
  RESOLUTION: 'Resolution', SYSTEM: 'System',
}

export function Conversation({ i, canNote, canInternal }: { i: Incident; canNote: boolean; canInternal: boolean }) {
  const user = useUser()
  const qc = useQueryClient()
  const staff = user.role !== 'EMPLOYEE'
  const [text, setText] = useState('')
  const [mode, setMode] = useState<'reply' | 'internal'>('reply')
  const end = useRef<HTMLDivElement>(null)
  const upload = useUpload(i.number)

  const add = useMutation({
    mutationFn: () => api.post<Incident>(`/incidents/${i.number}/notes`, {
      type: staff && mode === 'internal' ? 'INVESTIGATION' : 'COMMENT', content: text.trim(), internal: canInternal && mode === 'internal',
    }),
    onSuccess: (d) => {
      qc.setQueryData(['incident', i.number], d)
      qc.invalidateQueries({ queryKey: ['incident', i.number, 'audit'] })
      setText('')
    },
  })

  const onPaste = (e: ClipboardEvent<HTMLTextAreaElement>) => {
    const file = [...e.clipboardData.files].find((f) => f.type.startsWith('image/'))
    if (file) {
      e.preventDefault()
      upload.mutate(new File([file], file.name && file.name !== 'image.png' ? file.name : `screenshot-${Date.now()}.png`, { type: file.type }))
    }
  }

  return (
    <div>
      {i.notes.length === 0 && <p className="text-[13px] text-muted">No messages yet.</p>}
      <ol className="space-y-3" aria-label="Conversation">
        {i.notes.map((n) => {
          if (n.type === 'SYSTEM') {
            return (
              <li key={n.id} className="flex items-center gap-2 py-1 text-xs text-muted">
                <Sparkles className="size-3.5 shrink-0 text-faint" aria-hidden />
                <span className="min-w-0">{n.content}</span>
                <span className="ml-auto shrink-0 tabular text-faint">{when(n.createdAt)}</span>
              </li>
            )
          }
          const mine = n.author === user.fullName
          const fromReporter = n.author === i.reporter.name
          return (
            <li key={n.id} className={cn('flex gap-2.5', mine && 'flex-row-reverse')}>
              <Avatar name={n.author} size={28} className="mt-0.5" />
              <div className={cn('max-w-[85%] min-w-0', mine && 'text-right')}>
                <div className={cn('flex flex-wrap items-baseline gap-x-2 text-xs text-muted', mine && 'justify-end')}>
                  <span className="text-[12.5px] font-medium text-ink">{mine ? 'You' : n.author}</span>
                  {!fromReporter && !mine && <span>IT</span>}
                  <span>{NOTE_LABEL[n.type]}</span>
                  {n.internal && <span className="inline-flex items-center gap-0.5 font-medium text-p2"><Lock className="size-3" />Internal</span>}
                  <span className="tabular">{when(n.createdAt)}</span>
                </div>
                <div className={cn('mt-1 inline-block whitespace-pre-wrap rounded-2xl px-3.5 py-2 text-left text-[13.5px] leading-relaxed',
                  n.internal ? 'border border-dashed border-p2/50 bg-p2-soft/60 text-ink'
                    : n.type === 'RESOLUTION' ? 'bg-ok-soft text-ink'
                      : n.type === 'INFO_REQUEST' ? 'border border-p2/40 bg-surface text-ink'
                        : mine ? 'rounded-tr-md bg-accent text-accent-ink' : 'rounded-tl-md bg-surface-2 text-ink')}>
                  {n.content}
                </div>
              </div>
            </li>
          )
        })}
      </ol>
      <div ref={end} />

      {canNote && (
        <form className="mt-5 rounded-xl border border-line bg-bg/50 p-2 focus-within:border-accent" onSubmit={(e) => { e.preventDefault(); if (text.trim()) add.mutate() }}>
          {canInternal && (
            <div className="mb-2 w-72 max-w-full">
              <Segmented name="Message type" value={mode} onChange={setMode}
                options={[{ value: 'reply', label: 'Reply to reporter' }, { value: 'internal', label: 'Internal note' }]} />
            </div>
          )}
          <Textarea rows={2} value={text} onChange={(e) => setText(e.target.value)} onPaste={onPaste}
            aria-label={mode === 'internal' ? 'Internal note' : 'Message'}
            onKeyDown={(e) => { if ((e.metaKey || e.ctrlKey) && e.key === 'Enter' && text.trim()) add.mutate() }}
            className={cn('border-0 bg-transparent focus:ring-0', mode === 'internal' && 'bg-p2-soft/40')}
            placeholder={mode === 'internal' ? 'Only IT staff will see this…' : staff ? `Message ${i.reporter.name.split(' ')[0]}…` : 'Write to the technician… (paste a screenshot to attach it)'} />
          <div className="flex items-center justify-between gap-3 px-1 pt-1">
            <AttachButton number={i.number} />
            <div className="flex items-center gap-2">
              <span className="hidden text-[11px] text-faint sm:inline">⌘ + Enter to send</span>
              <Button type="submit" size="sm" variant="primary" icon={<Send className="size-3.5" />} loading={add.isPending} disabled={!text.trim()}>
                {mode === 'internal' ? 'Add note' : 'Send'}
              </Button>
            </div>
          </div>
          {add.error && <div className="mt-2"><ErrorNote error={add.error} /></div>}
        </form>
      )}
    </div>
  )
}

// ---------------------------------------------------------------------------------------------
// Attachments
// ---------------------------------------------------------------------------------------------

function useUpload(number: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (file: File) => api.upload<AttachmentView>(`/incidents/${number}/attachments`, file),
    onSuccess: (a) => {
      qc.invalidateQueries({ queryKey: ['attachments', number] })
      qc.invalidateQueries({ queryKey: ['incident', number] })
      toast.success(`Attached ${a.fileName}`)
    },
    onError: (err) => toast.error(err instanceof Error ? err.message : 'Upload failed'),
  })
}

export const ACCEPT = '.png,.jpg,.jpeg,.gif,.webp,.pdf,.txt,.log,.csv,.json,.xml,.yml,.yaml,.md,.docx,.xlsx,.pptx'

function AttachButton({ number }: { number: string }) {
  const input = useRef<HTMLInputElement>(null)
  const upload = useUpload(number)
  return (
    <>
      <input ref={input} type="file" className="sr-only" accept={ACCEPT} multiple tabIndex={-1}
        onChange={(e) => { [...(e.target.files ?? [])].forEach((f) => upload.mutate(f)); e.target.value = '' }} />
      <Button type="button" size="sm" variant="ghost" icon={<Paperclip className="size-3.5" />} loading={upload.isPending}
        onClick={() => input.current?.click()}>Attach</Button>
    </>
  )
}

export function Attachments({ i, canUpload }: { i: Incident; canUpload: boolean }) {
  const qc = useQueryClient()
  const files = useQuery({ queryKey: ['attachments', i.number], queryFn: () => api.get<AttachmentView[]>(`/incidents/${i.number}/attachments`) })
  const upload = useUpload(i.number)
  const remove = useMutation({
    mutationFn: (id: number) => api.delete(`/attachments/${id}`),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['attachments', i.number] }); qc.invalidateQueries({ queryKey: ['incident', i.number] }) },
    onError: (err) => toast.error(err instanceof Error ? err.message : 'Could not remove'),
  })
  const [drag, setDrag] = useState(false)
  const input = useRef<HTMLInputElement>(null)
  const list = files.data ?? []

  const onDrop = (e: DragEvent) => {
    e.preventDefault()
    setDrag(false)
    if (canUpload) [...e.dataTransfer.files].forEach((f) => upload.mutate(f))
  }

  if (!canUpload && !list.length && !files.isLoading) return null
  return (
    <section className="rounded-xl border border-line bg-surface p-4" aria-label="Attachments"
      onDragOver={(e) => { if (canUpload) { e.preventDefault(); setDrag(true) } }} onDragLeave={() => setDrag(false)} onDrop={onDrop}>
      <h2 className="mb-3 flex items-center justify-between text-[13.5px] font-semibold">
        <span>Files {list.length > 0 && <span className="font-normal text-muted">({list.length})</span>}</span>
      </h2>
      {files.isLoading ? <Skeleton className="h-16" /> : (
        <ul className="space-y-2">
          {list.map((a) => <AttachmentRow key={a.id} a={a} onDelete={() => remove.mutate(a.id)} />)}
        </ul>
      )}
      {canUpload && (
        <>
          <input ref={input} type="file" className="sr-only" accept={ACCEPT} multiple tabIndex={-1}
            onChange={(e) => { [...(e.target.files ?? [])].forEach((f) => upload.mutate(f)); e.target.value = '' }} />
          <button type="button" onClick={() => input.current?.click()}
            className={cn('mt-3 flex w-full flex-col items-center gap-1 rounded-lg border border-dashed px-3 py-4 text-center text-xs text-muted transition-colors cursor-pointer',
              drag ? 'border-accent bg-accent-soft text-accent-text' : 'border-line-strong hover:border-accent hover:text-ink')}>
            <Upload className="size-4" aria-hidden />
            {upload.isPending ? 'Uploading…' : 'Drop files here or click to choose'}
            <span className="text-[11px] text-faint">Images, PDF, logs or Office files · up to 5 MB · virus-scanned</span>
          </button>
        </>
      )}
    </section>
  )
}

function AttachmentRow({ a, onDelete }: { a: AttachmentView; onDelete: () => void }) {
  const [thumb, setThumb] = useState<string | null>(null)
  const [preview, setPreview] = useState(false)
  useEffect(() => {
    if (!a.image) return
    let url: string | null = null
    fetchBlob(`/attachments/${a.id}`).then((b) => { url = URL.createObjectURL(b); setThumb(url) }).catch(() => undefined)
    return () => { if (url) URL.revokeObjectURL(url) }
  }, [a.id, a.image])
  const size = a.sizeBytes < 1024 * 1024 ? `${Math.max(1, Math.round(a.sizeBytes / 1024))} KB` : `${(a.sizeBytes / 1048576).toFixed(1)} MB`
  return (
    <li className="flex items-center gap-3">
      {a.image ? (
        <button onClick={() => setPreview(true)} className="size-10 shrink-0 overflow-hidden rounded-md border border-line bg-surface-2 cursor-zoom-in" aria-label={`Preview ${a.fileName}`}>
          {thumb ? <img src={thumb} alt="" className="size-full object-cover" /> : <ImageIcon className="m-auto size-4 text-faint" />}
        </button>
      ) : (
        <span className="flex size-10 shrink-0 items-center justify-center rounded-md bg-surface-2 text-muted"><FileText className="size-4" aria-hidden /></span>
      )}
      <div className="min-w-0 flex-1">
        <p className="truncate text-[13px] font-medium">{a.fileName}</p>
        <p className="text-[11px] text-muted">{size} · {a.uploadedBy} · {ago(a.uploadedAt)}</p>
      </div>
      <button onClick={() => downloadFile(`/attachments/${a.id}`, a.fileName)} aria-label={`Download ${a.fileName}`} className="rounded-md p-1.5 text-muted hover:bg-surface-2 hover:text-ink cursor-pointer"><Download className="size-4" /></button>
      {a.canDelete && <button onClick={onDelete} aria-label={`Remove ${a.fileName}`} className="rounded-md p-1.5 text-muted hover:bg-p1-soft hover:text-p1 cursor-pointer"><Trash2 className="size-4" /></button>}
      {preview && thumb && (
        <Dialog open onOpenChange={setPreview} title={a.fileName} width={860}>
          <img src={thumb} alt={a.fileName} className="mx-auto max-h-[60vh] rounded-lg" />
        </Dialog>
      )}
    </li>
  )
}

// ---------------------------------------------------------------------------------------------
// Major incidents
// ---------------------------------------------------------------------------------------------

const STAGES: { value: MajorStage; label: string }[] = [
  { value: 'INVESTIGATING', label: 'Investigating' },
  { value: 'IDENTIFIED', label: 'Cause identified' },
  { value: 'MONITORING', label: 'Monitoring' },
  { value: 'RESOLVED', label: 'Resolved' },
]

export function MajorPanel({ i, canUpdate, onUpdate }: { i: Incident; canUpdate: boolean; onUpdate: () => void }) {
  const m = i.major!
  const current = STAGES.findIndex((s) => s.value === m.stage)
  return (
    <section className="overflow-hidden rounded-xl border border-p1/35 bg-surface" aria-label="Major incident">
      <header className="flex flex-wrap items-center gap-3 border-b border-p1/20 bg-p1-soft px-5 py-3">
        <Siren className="size-4 text-p1" aria-hidden />
        <h2 className="text-[15px] font-semibold text-p1">Major incident</h2>
        <span className="text-xs text-muted">declared {ago(m.declaredAt)} · {m.linked.length} linked report{m.linked.length === 1 ? '' : 's'}</span>
        {canUpdate && <Button size="sm" className="ml-auto" icon={<Megaphone className="size-3.5" />} onClick={onUpdate}>Post update</Button>}
      </header>
      <div className="px-5 py-4">
        <ol className="grid grid-cols-4 gap-2" aria-label="Stage">
          {STAGES.map((s, idx) => (
            <li key={s.value} className="min-w-0">
              <div className={cn('h-1 rounded-full', idx <= current ? (m.stage === 'RESOLVED' ? 'bg-ok' : 'bg-p1') : 'bg-surface-3')} />
              <p className={cn('mt-1.5 truncate text-xs', idx === current ? 'font-semibold text-ink' : 'text-muted')} aria-current={idx === current ? 'step' : undefined}>{s.label}</p>
            </li>
          ))}
        </ol>
        <ol className="mt-5 space-y-3 border-l border-line pl-4">
          {m.updates.map((u, idx) => (
            <li key={idx} className="relative">
              <span className={cn('absolute -left-[21px] top-1.5 size-2.5 rounded-full border-2 border-surface', idx === 0 ? 'bg-p1' : 'bg-line-strong')} />
              <p className="text-xs text-muted"><span className="font-medium text-ink-2">{u.stageLabel}</span> · {u.author} · {when(u.at)}</p>
              <p className="mt-0.5 text-[13.5px]">{u.message}</p>
            </li>
          ))}
        </ol>
        {m.linked.length > 0 && (
          <details className="mt-4 border-t border-line pt-3">
            <summary className="cursor-pointer text-[13px] font-medium text-ink-2">Linked incidents ({m.linked.length})</summary>
            <ul className="mt-2 space-y-1.5">
              {m.linked.map((l) => (
                <li key={l.number} className="flex items-center gap-2 text-[13px]">
                  <Link to={`/incidents/${l.number}`} className="tabular font-medium text-accent-text hover:underline">{l.number}</Link>
                  <span className="min-w-0 flex-1 truncate">{l.title}</span>
                  <span className="text-xs text-muted">{l.reporter.name}</span>
                  <StatusBadge status={l.status} />
                </li>
              ))}
            </ul>
          </details>
        )}
      </div>
    </section>
  )
}

export function MajorParentNotice({ i }: { i: Incident }) {
  const p = i.majorParent!
  return (
    <section className="flex gap-3 rounded-xl border border-p1/30 bg-p1-soft/60 px-5 py-4">
      <Siren className="mt-0.5 size-4 shrink-0 text-p1" aria-hidden />
      <div className="min-w-0">
        <p className="text-[13.5px] font-medium">
          Part of a wider problem: <Link to={`/incidents/${p.number}`} className="text-accent-text hover:underline">{p.title}</Link>
        </p>
        <p className="mt-0.5 text-[13px] text-muted">
          {p.stageLabel}{p.latestUpdate ? ` — ${p.latestUpdate.message}` : ''}
        </p>
        <p className="mt-1 text-xs text-muted">You'll get every status update, and this incident is resolved automatically when the main one is fixed.</p>
      </div>
    </section>
  )
}

export function DeclareMajorDialog({ i, onClose, onDone }: { i: Incident; onClose: () => void; onDone: (d: Incident) => void }) {
  const similar = useQuery({ queryKey: ['incident', i.number, 'similar'], queryFn: () => api.get<SimilarIncident[]>(`/incidents/${i.number}/similar`) })
  const [summary, setSummary] = useState(`We are aware that ${i.service.name} is affected and are investigating.`)
  const [picked, setPicked] = useState<string[]>([])
  const declare = useMutation({
    mutationFn: () => api.post<Incident>(`/incidents/${i.number}/major`, { summary, link: picked }),
    onSuccess: onDone,
  })
  const candidates = (similar.data ?? []).filter((s) => s.score >= 0.2)
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()} title="Declare a major incident" width={540}
      description="Supervisors, managers and everyone affected are alerted. Status updates go to every linked reporter, and resolving this resolves them all."
      footer={<><Button variant="ghost" onClick={onClose}>Cancel</Button><Button variant="danger" loading={declare.isPending} disabled={!summary.trim()} onClick={() => declare.mutate()}>Declare major incident</Button></>}>
      <div className="space-y-4">
        <Field label="First public update" htmlFor="maj-summary" hint="Shown in the banner every user sees">
          <Textarea id="maj-summary" rows={3} value={summary} onChange={(e) => setSummary(e.target.value)} />
        </Field>
        <div>
          <p className="mb-2 text-[13px] font-medium text-ink-2">Link related incidents now</p>
          {similar.isLoading ? <Skeleton className="h-12" /> : candidates.length === 0 ? (
            <p className="text-xs text-muted">No similar open incidents found. You can link more later from each incident.</p>
          ) : (
            <ul className="max-h-48 space-y-1 overflow-y-auto">
              {candidates.map((c) => (
                <li key={c.number}>
                  <label className="flex cursor-pointer items-center gap-2 rounded-md px-2 py-1.5 text-[13px] hover:bg-surface-2">
                    <input type="checkbox" className="accent-[var(--accent)]" checked={picked.includes(c.number)}
                      onChange={(e) => setPicked(e.target.checked ? [...picked, c.number] : picked.filter((x) => x !== c.number))} />
                    <span className="tabular text-muted">{c.number}</span>
                    <span className="min-w-0 flex-1 truncate">{c.title}</span>
                    <span className="text-xs text-faint">{c.service}</span>
                  </label>
                </li>
              ))}
            </ul>
          )}
        </div>
        {declare.error ? <ErrorNote error={declare.error} /> : null}
      </div>
    </Dialog>
  )
}

export function MajorUpdateDialog({ i, onClose, onDone }: { i: Incident; onClose: () => void; onDone: (d: Incident) => void }) {
  const [stage, setStage] = useState<MajorStage>(i.major?.stage === 'INVESTIGATING' ? 'IDENTIFIED' : (i.major?.stage ?? 'INVESTIGATING'))
  const [message, setMessage] = useState('')
  const post = useMutation({ mutationFn: () => api.post<Incident>(`/incidents/${i.number}/major/updates`, { stage, message }), onSuccess: onDone })
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()} title="Post a status update" width={520}
      description={`Sent to ${(i.major?.linked.length ?? 0) + 1} reporter(s) by e-mail and in the app, and shown in everyone's banner.`}
      footer={<><Button variant="ghost" onClick={onClose}>Cancel</Button><Button variant="primary" loading={post.isPending} disabled={!message.trim()} onClick={() => post.mutate()}>Post update</Button></>}>
      <div className="space-y-4">
        <Field label="Stage">
          <Segmented name="Stage" value={stage} onChange={setStage} options={STAGES.filter((s) => s.value !== 'RESOLVED')} />
        </Field>
        <Field label="Update" htmlFor="maj-msg" hint="Plain words: what's affected, what you're doing, when to expect the next update">
          <Textarea id="maj-msg" rows={4} value={message} onChange={(e) => setMessage(e.target.value)} />
        </Field>
        <p className="text-xs text-muted">To finish, resolve this incident: the stage becomes “Resolved” and linked incidents are resolved with it.</p>
        {post.error ? <ErrorNote error={post.error} /> : null}
      </div>
    </Dialog>
  )
}

export function LinkMajorDialog({ i, onClose, onDone }: { i: Incident; onClose: () => void; onDone: (d: Incident) => void }) {
  const majors = useQuery({ queryKey: ['major-incidents'], queryFn: () => api.get<MajorBanner[]>('/major-incidents') })
  const [choice, setChoice] = useState('')
  const link = useMutation({ mutationFn: () => api.post<Incident>(`/incidents/${i.number}/link-major`, { majorNumber: choice }), onSuccess: onDone })
  const list = (majors.data ?? []).filter((m) => m.number !== i.number)
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()} title="Link to a major incident"
      description="The reporter will receive the major incident's updates, and this incident is resolved when it is."
      footer={<><Button variant="ghost" onClick={onClose}>Cancel</Button><Button variant="primary" icon={<Link2 className="size-4" />} loading={link.isPending} disabled={!choice} onClick={() => link.mutate()}>Link</Button></>}>
      {majors.isLoading ? <Skeleton className="h-16" /> : list.length === 0 ? (
        <p className="text-[13px] text-muted">There are no open major incidents. A team lead can declare one from the incident at the centre of an outage.</p>
      ) : (
        <ul className="space-y-1.5" role="radiogroup" aria-label="Major incident">
          {list.map((m) => (
            <li key={m.number}>
              <label className={cn('flex cursor-pointer items-start gap-3 rounded-lg border px-3 py-2.5', choice === m.number ? 'border-accent bg-accent-soft/40' : 'border-line hover:bg-surface-2')}>
                <input type="radio" name="major" className="mt-1 accent-[var(--accent)]" checked={choice === m.number} onChange={() => setChoice(m.number)} />
                <span className="min-w-0">
                  <span className="block text-[13.5px] font-medium">{m.title}</span>
                  <span className="block text-xs text-muted">{m.number} · {m.service.name} · {m.stageLabel} · {m.linkedCount} linked</span>
                </span>
              </label>
            </li>
          ))}
        </ul>
      )}
      {link.error ? <div className="mt-3"><ErrorNote error={link.error} /></div> : null}
    </Dialog>
  )
}
