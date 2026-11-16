import { useCallback, useEffect, useRef, useState } from 'react'

// Voice-based incident reporting: dictation with the browser's own Web Speech API. Nothing is
// installed and no audio is sent to ResolveIQ - the browser turns speech into text, and the text is
// then used exactly like typed text. Where the browser has no speech recognition (Firefox, some
// embedded browsers) `supported` is false and callers show a plain message instead.

interface RecognitionResult { isFinal: boolean; 0: { transcript: string } }
interface RecognitionEvent { resultIndex: number; results: ArrayLike<RecognitionResult> }
interface Recognition {
  lang: string; continuous: boolean; interimResults: boolean
  onresult: ((e: RecognitionEvent) => void) | null
  onerror: ((e: { error: string }) => void) | null
  onend: (() => void) | null
  start: () => void; stop: () => void; abort: () => void
}
type RecognitionCtor = new () => Recognition

function recognitionCtor(): RecognitionCtor | undefined {
  const w = window as unknown as { SpeechRecognition?: RecognitionCtor; webkitSpeechRecognition?: RecognitionCtor }
  return w.SpeechRecognition ?? w.webkitSpeechRecognition
}

export const speechSupported = () => typeof window !== 'undefined' && !!recognitionCtor()

const ERRORS: Record<string, string> = {
  'not-allowed': 'Microphone access was blocked. Allow it in your browser\'s site settings, or type instead.',
  'service-not-allowed': 'Microphone access was blocked. Allow it in your browser\'s site settings, or type instead.',
  'no-speech': 'I didn\'t hear anything. Try again, or type instead.',
  'audio-capture': 'No microphone was found. Check that one is connected, or type instead.',
  network: 'Voice input needs an internet connection in this browser. Please type instead.',
}

/**
 * Dictation into a text field. `onText` receives each finished phrase; `interim` is the phrase
 * being spoken right now (for a live preview).
 */
export function useDictation(onText: (text: string) => void) {
  const [listening, setListening] = useState(false)
  const [interim, setInterim] = useState('')
  const [error, setError] = useState<string | null>(null)
  const recognition = useRef<Recognition | null>(null)
  const handler = useRef(onText)
  handler.current = onText
  const supported = speechSupported()

  const stop = useCallback(() => { recognition.current?.stop() }, [])

  const start = useCallback(() => {
    const Ctor = recognitionCtor()
    if (!Ctor) {
      setError('Voice input isn\'t available in this browser. Try Chrome, Edge or Safari, or type instead.')
      return
    }
    setError(null)
    const r = new Ctor()
    r.lang = navigator.language?.startsWith('en') ? navigator.language : 'en-IN'
    r.continuous = false
    r.interimResults = true
    r.onresult = (e) => {
      let live = ''
      for (let i = e.resultIndex; i < e.results.length; i++) {
        const res = e.results[i]
        if (res.isFinal) handler.current(res[0].transcript.trim())
        else live += res[0].transcript
      }
      setInterim(live)
    }
    r.onerror = (e) => { if (e.error !== 'aborted') setError(ERRORS[e.error] ?? 'Voice input stopped unexpectedly. Please type instead.') }
    r.onend = () => { setListening(false); setInterim(''); recognition.current = null }
    recognition.current = r
    try { r.start(); setListening(true) } catch { setListening(false) }
  }, [])

  useEffect(() => () => recognition.current?.abort(), [])

  return { supported, listening, interim, error, start, stop, toggle: () => (listening ? stop() : start()) }
}
