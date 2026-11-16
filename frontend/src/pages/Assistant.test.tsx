import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { fakeApi } from '@/test/fake-api'
import type { AssistantReply } from '@/lib/types'
import Assistant from './Assistant'

const prompt = (over: Partial<NonNullable<AssistantReply['prompt']>>) => ({
  text: '', options: [], allowText: false, articles: [], incidents: [], ...over,
})
const turn = (over: Partial<AssistantReply>): AssistantReply => ({
  sessionId: 7, step: 'DESCRIBE_PROBLEM', done: false, outcome: 'OPEN', messages: [], ...over,
})

const GREETING = turn({ messages: ['Hi Ava, I\'m the ResolveIQ assistant.', 'What\'s going wrong?'], prompt: prompt({ allowText: true, placeholder: 'Describe the problem…' }) })
const SELF_HELP = turn({
  step: 'OFFER_SELF_HELP', messages: ['Thanks.', 'Before I log this: this guide has fixed the same problem for other people.'],
  prompt: prompt({
    articles: [{ id: 3, title: 'Reconnect to campus Wi-Fi', symptoms: 'Wi-Fi keeps dropping', solution: 'Forget the network, then join again.', deflections: 12 }],
    options: [{ value: 'FIXED:3', label: 'This fixed it' }, { value: 'NOT_FIXED', label: 'It didn\'t help' }],
  }),
})
const SUMMARY = turn({
  step: 'CONFIRM_SUMMARY', messages: ['No problem - let\'s get this to the IT team.', 'Here\'s what I\'ll send to the IT team. Shall I submit it?'],
  prompt: prompt({
    summary: { title: 'Wi-Fi keeps dropping', description: 'Wi-Fi keeps dropping in the library', service: 'Campus Wi-Fi', category: 'Network', categoryConfidence: 0.83, impact: 'A team or department is affected', urgency: 'Needed within hours; work is blocked', priority: 'P2', priorityLabel: 'High', responseMinutes: 15, resolutionMinutes: 120 },
    options: [{ value: 'SUBMIT', label: 'Submit' }],
  }),
})
const CREATED = turn({ step: 'FINISHED', done: true, outcome: 'INCIDENT_CREATED', incidentNumber: 'INC-2001', messages: ['Done - INC-2001 is logged as P2 (High).'] })

function renderAssistant() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter><Assistant /></MemoryRouter>
    </QueryClientProvider>,
  )
}

afterEach(() => { vi.unstubAllGlobals() })

describe('Report with assistant', () => {
  it('FE-21 a conversation: describe, see a guide inline, decline it, confirm the summary, get the incident link', async () => {
    const answers = [SELF_HELP, SUMMARY, CREATED]
    const { calls } = fakeApi({
      'POST /api/assistant/sessions': { status: 201, body: GREETING },
      'POST /api/assistant/sessions/7/messages': () => ({ body: answers.shift() }),
    })
    const user = userEvent.setup()
    renderAssistant()
    expect(await screen.findByText('What\'s going wrong?')).toBeInTheDocument()

    await user.type(screen.getByLabelText('Your message'), 'Wi-Fi keeps dropping in the library')
    await user.click(screen.getByRole('button', { name: 'Send' }))
    expect(await screen.findByText('Reconnect to campus Wi-Fi')).toBeInTheDocument()
    expect(screen.getByText('Forget the network, then join again.')).toBeVisible() // the fix is shown inline
    expect(calls.at(-1)?.body).toEqual({ text: 'Wi-Fi keeps dropping in the library' })

    await user.click(screen.getByRole('button', { name: 'It didn\'t help' }))
    expect(calls.at(-1)?.body).toEqual({ choice: 'NOT_FIXED', text: 'It didn\'t help' })
    expect(await screen.findByText('Campus Wi-Fi')).toBeInTheDocument()
    expect(screen.getByText(/suggested by AI \(83% sure\)/)).toBeInTheDocument()
    expect(screen.getByText(/first response within 15 min, fix within 2 h/)).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Submit' }))
    expect(await screen.findByText('Incident reported')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Open INC-2001' })).toHaveAttribute('href', '/incidents/INC-2001')
    expect(screen.queryByLabelText('Your message')).not.toBeInTheDocument() // a finished conversation takes no more input

    // the whole exchange is in the log, in order, labelled for screen readers
    const log = within(screen.getByRole('log'))
    expect(log.getByText('Wi-Fi keeps dropping in the library').textContent).toContain('You: ')
    expect(calls.filter((c) => c.url === '/api/assistant/sessions')).toHaveLength(1)
  })

  it('FE-22 start over and cancel are sent as commands; a failed turn shows the error and keeps the conversation', async () => {
    let fail = true
    const { calls } = fakeApi({
      'POST /api/assistant/sessions': { status: 201, body: GREETING },
      'POST /api/assistant/sessions/7/messages': () => (fail
        ? { status: 400, body: { code: 'VALIDATION_FAILED', detail: 'Please keep it under 4,000 characters' } }
        : { body: turn({ step: 'FINISHED', done: true, outcome: 'CANCELLED', messages: ['OK, I\'ve cancelled this. Nothing was created.'] }) }),
    })
    const user = userEvent.setup()
    renderAssistant()
    await screen.findByText('What\'s going wrong?')
    await user.click(screen.getByRole('button', { name: 'Start over' }))
    expect(calls.at(-1)?.body).toEqual({ choice: 'RESTART' })
    expect(await screen.findByRole('alert')).toBeInTheDocument()
    expect(screen.getByLabelText('Your message')).toBeEnabled()

    fail = false
    await user.click(screen.getByRole('button', { name: 'Cancel' }))
    expect(await screen.findByText('Cancelled - nothing was created')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'New conversation' })).toBeInTheDocument()
  })

  it('FE-23 voice: dictated speech lands in the message box; without browser support the button says so', async () => {
    fakeApi({ 'POST /api/assistant/sessions': { status: 201, body: GREETING } })
    const user = userEvent.setup()

    // no speech recognition in this browser (jsdom): the button is still there and explains itself
    const first = renderAssistant()
    await screen.findByText('What\'s going wrong?')
    expect(screen.getByRole('button', { name: 'Voice input (not available in this browser)' })).toBeInTheDocument()
    first.unmount()

    // a browser with the Web Speech API
    let recogniser: { onresult?: (e: unknown) => void; onend?: () => void } = {}
    class FakeRecognition {
      lang = ''; continuous = false; interimResults = false
      onresult?: (e: unknown) => void; onerror?: (e: unknown) => void; onend?: () => void
      start() { recogniser = this }
      stop() { this.onend?.() }
      abort() {}
    }
    vi.stubGlobal('webkitSpeechRecognition', FakeRecognition)
    renderAssistant()
    await screen.findByText('What\'s going wrong?')
    await user.click(screen.getByRole('button', { name: 'Dictate with your voice' }))
    expect(screen.getByRole('button', { name: 'Stop dictating' })).toHaveAttribute('aria-pressed', 'true')
    recogniser.onresult?.({ resultIndex: 0, results: [{ isFinal: true, 0: { transcript: ' the printer is offline ' } }] })
    await waitFor(() => expect(screen.getByLabelText('Your message')).toHaveValue('the printer is offline'))
    await user.click(screen.getByRole('button', { name: 'Stop dictating' }))
    expect(screen.getByRole('button', { name: 'Dictate with your voice' })).toBeInTheDocument()
  })
})
