import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { BookOpen, CheckCircle2, CircleSlash, Copy, RotateCcw, SendHorizontal, Siren, Sparkles } from 'lucide-react'
import { api } from '@/lib/api'
import { cn, duration } from '@/lib/format'
import type { AssistantPrompt, AssistantReply, AssistantSummary, ConversationOutcome } from '@/lib/types'
import { Button, ErrorNote, Input, PageHeader, Spinner } from '@/components/ui'
import { PriorityBadge } from '@/components/incident-bits'
import { MicButton } from '@/components/MicButton'

interface Line { from: 'assistant' | 'user'; text: string }

/**
 * "Report with assistant": a guided conversation as an alternative to the form. The server runs the
 * conversation (see ChatbotService); this page only shows each turn and sends back what the person
 * typed, dictated or tapped.
 */
export default function Assistant() {
  const qc = useQueryClient()
  const [lines, setLines] = useState<Line[]>([])
  const [reply, setReply] = useState<AssistantReply | null>(null)
  const [draft, setDraft] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const started = useRef(false)
  const end = useRef<HTMLDivElement>(null)
  const input = useRef<HTMLInputElement>(null)

  const accept = useCallback((r: AssistantReply) => {
    setReply(r)
    setLines((l) => [...l, ...r.messages.map((text) => ({ from: 'assistant' as const, text }))])
    if (r.done) {
      // whatever happened (incident created, joined an outage) other screens must show it
      qc.invalidateQueries({ queryKey: ['incidents'] })
      qc.invalidateQueries({ queryKey: ['dashboard'] })
      qc.invalidateQueries({ queryKey: ['major-incidents'] })
    }
  }, [qc])

  const begin = useCallback(async () => {
    setBusy(true); setError(null); setLines([]); setReply(null); setDraft('')
    try { accept(await api.post<AssistantReply>('/assistant/sessions')) } catch (e) { setError(e) } finally { setBusy(false) }
  }, [accept])

  // start exactly one conversation (React StrictMode runs effects twice in development)
  useEffect(() => { if (!started.current) { started.current = true; void begin() } }, [begin])

  useEffect(() => { end.current?.scrollIntoView?.({ block: 'nearest' }) }, [lines, busy])

  const send = async (body: { text?: string; choice?: string }, shown: string) => {
    if (!reply || busy) return
    setLines((l) => [...l, { from: 'user', text: shown }])
    setDraft(''); setBusy(true); setError(null)
    try { accept(await api.post<AssistantReply>(`/assistant/sessions/${reply.sessionId}/messages`, body)) }
    catch (e) { setError(e) } finally { setBusy(false); input.current?.focus() }
  }

  const submit = (e: FormEvent) => {
    e.preventDefault()
    const text = draft.trim()
    if (text) void send({ text }, text)
  }

  const prompt = reply?.done ? undefined : reply?.prompt
  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader title="Report with assistant"
        description="Tell the assistant what's wrong. It suggests fixes, checks for known problems and reports it for you."
        actions={<Link to="/report"><Button size="sm">Use the form instead</Button></Link>} />

      <section aria-label="Conversation" className="rounded-xl border border-line bg-surface">
        <div role="log" aria-live="polite" aria-relevant="additions" className="space-y-3 px-4 py-5 sm:px-6">
          {lines.map((l, i) => (
            <div key={i} className={cn('flex', l.from === 'user' ? 'justify-end' : 'justify-start')}>
              {l.from === 'assistant' && <Sparkles className="mr-2 mt-2 size-4 shrink-0 text-accent-text" aria-hidden />}
              <p className={cn('max-w-[85%] whitespace-pre-line rounded-2xl px-3.5 py-2 text-sm',
                l.from === 'user' ? 'rounded-br-md bg-accent text-accent-ink' : 'rounded-bl-md bg-surface-2 text-ink')}>
                <span className="sr-only">{l.from === 'user' ? 'You: ' : 'Assistant: '}</span>{l.text}
              </p>
            </div>
          ))}
          {busy && <div className="flex items-center gap-2 pl-6 text-[0.8125rem] text-muted"><Spinner /> {lines.length ? 'Thinking…' : 'Starting…'}</div>}
          {error ? <ErrorNote error={error} /> : null}
          <div ref={end} />
        </div>

        {prompt && <PromptCards prompt={prompt} />}

        {prompt && prompt.options.length > 0 && (
          <div role="group" aria-label="Suggested answers" className="flex flex-wrap gap-2 border-t border-line px-4 py-3 sm:px-6">
            {prompt.options.map((o) => (
              <button key={o.value} type="button" disabled={busy} title={o.hint} onClick={() => send({ choice: o.value, text: o.label }, o.label)}
                className={cn('rounded-full border px-3 py-1.5 text-left text-[0.8125rem] font-medium disabled:opacity-50 cursor-pointer',
                  o.value === 'SUBMIT' ? 'border-accent bg-accent text-accent-ink hover:bg-accent-hover'
                    : 'border-line-strong bg-surface text-ink hover:border-accent hover:bg-accent-soft')}>
                {o.label}
              </button>
            ))}
          </div>
        )}

        {reply?.done ? <Outcome reply={reply} onRestart={begin} /> : (
          <form onSubmit={submit} className="flex items-center gap-2 border-t border-line px-4 py-3 sm:px-6">
            <label htmlFor="assistant-input" className="sr-only">Your message</label>
            <Input id="assistant-input" ref={input} value={draft} onChange={(e) => setDraft(e.target.value)} maxLength={4000}
              autoComplete="off" disabled={!reply} placeholder={prompt?.placeholder ?? 'Type an answer, or pick an option above…'} />
            <MicButton disabled={!reply || busy} onText={(t) => setDraft((d) => (d ? `${d} ${t}` : t))} />
            <Button type="submit" variant="primary" disabled={!draft.trim() || busy} icon={<SendHorizontal className="size-4" />}>Send</Button>
          </form>
        )}
      </section>

      {reply && !reply.done && (
        <div className="mt-3 flex flex-wrap items-center gap-1 text-[0.8125rem]">
          <Button size="sm" variant="ghost" disabled={busy} icon={<RotateCcw className="size-3.5" />} onClick={() => send({ choice: 'RESTART' }, 'Start over')}>Start over</Button>
          <Button size="sm" variant="ghost" disabled={busy} icon={<CircleSlash className="size-3.5" />} onClick={() => send({ choice: 'CANCEL' }, 'Cancel')}>Cancel</Button>
          <span className="ml-auto text-xs text-muted">Nothing is created until you confirm the summary.</span>
        </div>
      )}
    </div>
  )
}

// ---------------------------------------------------------------------------------------------

function PromptCards({ prompt }: { prompt: AssistantPrompt }) {
  if (!prompt.articles.length && !prompt.incidents.length && !prompt.major && !prompt.summary) return null
  return (
    <div className="space-y-3 border-t border-line px-4 py-4 sm:px-6">
      {prompt.major && (
        <div className="flex gap-3 rounded-lg border border-p1/30 bg-p1-soft px-4 py-3">
          <Siren className="mt-0.5 size-4 shrink-0 text-p1" aria-hidden />
          <div className="min-w-0 text-[0.8125rem]">
            <p className="font-semibold text-p1">Major incident {prompt.major.number}{prompt.major.stage ? ` · ${prompt.major.stage}` : ''}</p>
            <p className="font-medium text-ink">{prompt.major.title}</p>
            {prompt.major.latestUpdate && <p className="mt-0.5 text-muted">Latest update: {prompt.major.latestUpdate}</p>}
          </div>
        </div>
      )}
      {prompt.articles.map((a, i) => (
        <details key={a.id} open={i === 0} className="group rounded-lg border border-line bg-surface-2/50 px-4 py-3">
          <summary className="flex cursor-pointer list-none items-center gap-2 text-sm font-medium text-ink">
            <BookOpen className="size-4 shrink-0 text-accent-text" aria-hidden />
            <span className="min-w-0 flex-1">{a.title}</span>
            <span className="text-xs font-normal text-muted group-open:hidden">Show the fix</span>
          </summary>
          <p className="mt-2 text-[0.8125rem] text-muted">{a.symptoms}</p>
          <p className="mt-2 whitespace-pre-line text-[0.8125rem] text-ink">{a.solution}</p>
          {a.deflections > 0 && <p className="mt-2 text-xs text-muted">This has fixed it for {a.deflections} {a.deflections === 1 ? 'person' : 'people'} without a ticket.</p>}
        </details>
      ))}
      {prompt.incidents.length > 0 && (
        <ul className="divide-y divide-line rounded-lg border border-line">
          {prompt.incidents.map((i) => (
            <li key={i.number} className="flex items-start gap-3 px-4 py-2.5 text-[0.8125rem]">
              <Copy className="mt-0.5 size-4 shrink-0 text-muted" aria-hidden />
              <div className="min-w-0">
                <p className="font-medium text-ink">{i.title}</p>
                <p className="text-muted">
                  <span className="tabular">{i.number}</span> · {i.service} · {i.status}{i.mine ? ' · reported by you' : ''}
                </p>
              </div>
            </li>
          ))}
        </ul>
      )}
      {prompt.summary && <SummaryCard summary={prompt.summary} />}
    </div>
  )
}

function SummaryCard({ summary: s }: { summary: AssistantSummary }) {
  const rows: [string, React.ReactNode][] = [
    ['Service', s.service],
    ['Category', s.category && <>{s.category}{s.categoryConfidence != null && <span className="text-muted"> · suggested by AI ({Math.round(s.categoryConfidence * 100)}% sure)</span>}</>],
    ['Who is affected', s.impact],
    ['Urgency', s.urgency],
    ['Device', s.device],
  ]
  return (
    <div className="rounded-lg border border-line">
      <div className="border-b border-line px-4 py-3">
        <p className="text-sm font-semibold text-ink">{s.title}</p>
        <p className="mt-1 whitespace-pre-line text-[0.8125rem] text-muted">{s.description}</p>
      </div>
      <dl className="grid gap-x-6 gap-y-2 px-4 py-3 text-[0.8125rem] sm:grid-cols-[9rem_1fr]">
        {rows.filter(([, v]) => v).map(([k, v]) => (
          <div key={k} className="contents"><dt className="text-muted">{k}</dt><dd className="text-ink">{v}</dd></div>
        ))}
      </dl>
      {s.priority && (
        <div className="flex flex-wrap items-center gap-2 border-t border-line bg-surface-2/50 px-4 py-2.5 text-[0.8125rem] text-ink-2">
          <span>Expected priority</span><PriorityBadge priority={s.priority} showLabel />
          {s.responseMinutes != null && s.resolutionMinutes != null && (
            <span className="text-muted">· first response within {duration(s.responseMinutes)}, fix within {duration(s.resolutionMinutes)}</span>
          )}
        </div>
      )}
    </div>
  )
}

const OUTCOME_TITLE: Record<ConversationOutcome, string> = {
  OPEN: '',
  INCIDENT_CREATED: 'Incident reported',
  JOINED_MAJOR_INCIDENT: 'Added to the major incident',
  DEFLECTED: 'Solved without a ticket',
  DUPLICATE_AVOIDED: 'Already reported - no new ticket',
  CANCELLED: 'Cancelled - nothing was created',
}

function Outcome({ reply, onRestart }: { reply: AssistantReply; onRestart: () => void }) {
  const heading = useRef<HTMLParagraphElement>(null)
  useEffect(() => { heading.current?.focus() }, [])
  const good = reply.outcome !== 'CANCELLED'
  return (
    <div className="flex flex-wrap items-center gap-3 border-t border-line bg-surface-2/50 px-4 py-4 sm:px-6">
      {good ? <CheckCircle2 className="size-5 shrink-0 text-ok" aria-hidden /> : <CircleSlash className="size-5 shrink-0 text-muted" aria-hidden />}
      <p ref={heading} tabIndex={-1} className="min-w-0 flex-1 text-sm font-medium text-ink outline-none">{OUTCOME_TITLE[reply.outcome]}</p>
      {reply.incidentNumber && <Link to={`/incidents/${reply.incidentNumber}`}><Button variant="primary" size="sm">Open {reply.incidentNumber}</Button></Link>}
      {reply.articleId && <Link to={`/kb/${reply.articleId}`}><Button size="sm">View the guide</Button></Link>}
      <Button size="sm" variant={reply.incidentNumber ? 'secondary' : 'primary'} onClick={onRestart}>New conversation</Button>
    </div>
  )
}
