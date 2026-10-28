import { useEffect } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { tokenStore } from './api'
import type { NotificationView } from './types'

/**
 * Subscribes to the server-sent event stream. New notifications pop up as toasts and every incident
 * change refreshes the affected queries, so dashboards update live without polling.
 */
export function useLiveUpdates(enabled: boolean, onOpenIncident: (number: string) => void) {
  const queryClient = useQueryClient()

  useEffect(() => {
    const token = tokenStore.get()
    if (!enabled || !token) return
    let source: EventSource | null = null
    let retry: ReturnType<typeof setTimeout> | undefined
    let invalidateTimer: ReturnType<typeof setTimeout> | undefined
    let closed = false

    const scheduleInvalidate = () => {
      // Many events arrive together (e.g. assign + status change); refresh once.
      clearTimeout(invalidateTimer)
      invalidateTimer = setTimeout(() => {
        queryClient.invalidateQueries({ queryKey: ['incidents'] })
        queryClient.invalidateQueries({ queryKey: ['incident'] })
        queryClient.invalidateQueries({ queryKey: ['dashboard'] })
      }, 350)
    }

    const connect = () => {
      source = new EventSource(`/api/notifications/stream?access_token=${encodeURIComponent(token)}`)
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
      source.onerror = () => {
        source?.close()
        if (!closed) retry = setTimeout(connect, 5000)
      }
    }
    connect()
    return () => {
      closed = true
      clearTimeout(retry)
      clearTimeout(invalidateTimer)
      source?.close()
    }
  }, [enabled, queryClient, onOpenIncident])
}
