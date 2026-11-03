import { useEffect } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { api, session } from './api'
import type { NotificationView } from './types'

/**
 * Subscribes to the server-sent event stream. New notifications pop up as toasts and every incident
 * change refreshes the affected queries, so dashboards update live without polling.
 *
 * EventSource cannot send an Authorization header, so each connection first asks for a one-time,
 * 60-second ticket; tokens never appear in URLs (or in server logs).
 */
export function useLiveUpdates(enabled: boolean, onOpenIncident: (number: string) => void) {
  const queryClient = useQueryClient()

  useEffect(() => {
    if (!enabled || !session.token) return
    let source: EventSource | null = null
    let retry: ReturnType<typeof setTimeout> | undefined
    let invalidateTimer: ReturnType<typeof setTimeout> | undefined
    let closed = false
    let failures = 0

    const scheduleInvalidate = () => {
      // Many events arrive together (e.g. assign + status change); refresh once.
      clearTimeout(invalidateTimer)
      invalidateTimer = setTimeout(() => {
        queryClient.invalidateQueries({ queryKey: ['incidents'] })
        queryClient.invalidateQueries({ queryKey: ['incident'] })
        queryClient.invalidateQueries({ queryKey: ['dashboard'] })
        queryClient.invalidateQueries({ queryKey: ['attachments'] })
      }, 350)
    }

    const connect = async () => {
      let ticket: string
      try {
        ticket = (await api.post<{ ticket: string }>('/notifications/stream-ticket')).ticket
      } catch {
        if (!closed) retry = setTimeout(connect, Math.min(30_000, 5_000 * ++failures))
        return
      }
      if (closed) return
      source = new EventSource(`/api/notifications/stream?ticket=${encodeURIComponent(ticket)}`)
      source.addEventListener('connected', () => { failures = 0 })
      source.addEventListener('notification', (e) => {
        const n = JSON.parse((e as MessageEvent).data) as Partial<NotificationView>
        queryClient.invalidateQueries({ queryKey: ['notifications'] })
        const show = n.urgent ? toast.error : toast
        show(n.subject ?? 'New notification', {
          description: n.message,
          action: n.incidentNumber
            ? { label: 'Open', onClick: () => onOpenIncident(n.incidentNumber!) }
            : undefined,
        })
      })
      source.addEventListener('incident', scheduleInvalidate)
      source.addEventListener('major', () => {
        queryClient.invalidateQueries({ queryKey: ['major-incidents'] })
        scheduleInvalidate()
      })
      source.onerror = () => {
        source?.close()
        if (!closed) retry = setTimeout(connect, Math.min(30_000, 5_000 * ++failures))
      }
    }
    void connect()
    return () => {
      closed = true
      clearTimeout(retry)
      clearTimeout(invalidateTimer)
      source?.close()
    }
  }, [enabled, queryClient, onOpenIncident])
}
