import { useEffect } from 'react'
import { Mic, MicOff, Square } from 'lucide-react'
import { toast } from 'sonner'
import { cn } from '@/lib/format'
import { useDictation } from '@/lib/speech'

/**
 * Microphone button for dictating into a text field (voice-based incident reporting). Spoken
 * phrases are passed to `onText`. In browsers without speech recognition the button stays visible
 * but explains, when pressed, that voice input isn't available there.
 */
export function MicButton({ onText, className, disabled }: { onText: (text: string) => void; className?: string; disabled?: boolean }) {
  const { supported, listening, interim, error, toggle } = useDictation(onText)

  useEffect(() => { if (error) toast.error(error) }, [error])

  const label = !supported ? 'Voice input (not available in this browser)' : listening ? 'Stop dictating' : 'Dictate with your voice'
  return (
    <>
      <button type="button" aria-label={label} title={label} aria-pressed={listening} disabled={disabled}
        onClick={toggle}
        className={cn('inline-flex size-9 shrink-0 items-center justify-center rounded-lg border transition-colors cursor-pointer disabled:opacity-50',
          listening ? 'border-p1 bg-p1 text-p1-ink' : 'border-line-strong bg-surface text-ink-2 hover:bg-surface-2', className)}>
        {!supported ? <MicOff className="size-4" aria-hidden /> : listening ? <Square className="size-3.5 fill-current fuse-live" aria-hidden /> : <Mic className="size-4" aria-hidden />}
      </button>
      {/* announced to screen readers; sighted users see the button change and the text appear */}
      <span className="sr-only" role="status">{listening ? `Listening. ${interim}` : ''}</span>
    </>
  )
}
