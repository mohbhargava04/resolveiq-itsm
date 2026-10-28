import { format, formatDistanceToNowStrict, isToday, isYesterday } from 'date-fns'
import type { IncidentStatus, Level, Priority, Role, SlaStatus } from './types'

export function cn(...parts: (string | false | null | undefined)[]) {
  return parts.filter(Boolean).join(' ')
}

/** "14:05" today, "Yesterday 14:05", otherwise "12 Oct, 14:05". */
export function when(iso?: string | null) {
  if (!iso) return '—'
  const d = new Date(iso)
  if (isToday(d)) return format(d, 'HH:mm')
  if (isYesterday(d)) return `Yesterday ${format(d, 'HH:mm')}`
  return format(d, 'd MMM, HH:mm')
}

export function fullDate(iso?: string | null) {
  if (!iso) return '—'
  return format(new Date(iso), "d MMM yyyy 'at' HH:mm")
}

export function ago(iso?: string | null) {
  if (!iso) return '—'
  return `${formatDistanceToNowStrict(new Date(iso))} ago`
}

/** Minutes as "45 min", "3 h 20 min", "2 d 4 h". */
export function duration(minutes?: number | null) {
  if (minutes === undefined || minutes === null) return '—'
  const m = Math.abs(Math.round(minutes))
  if (m < 60) return `${m} min`
  const h = Math.floor(m / 60)
  const rem = m % 60
  if (h < 24) return rem ? `${h} h ${rem} min` : `${h} h`
  const d = Math.floor(h / 24)
  const hr = h % 24
  return hr ? `${d} d ${hr} h` : `${d} d`
}

/** "34 min left" / "12 min overdue". */
export function remaining(minutes?: number | null) {
  if (minutes === undefined || minutes === null) return ''
  return minutes >= 0 ? `${duration(minutes)} left` : `${duration(minutes)} overdue`
}

export const STATUS_LABEL: Record<IncidentStatus, string> = {
  NEW: 'New',
  CLASSIFIED: 'Classified',
  ASSIGNED: 'Assigned',
  ACKNOWLEDGED: 'Acknowledged',
  IN_PROGRESS: 'In progress',
  AWAITING_INFO: 'Awaiting info',
  RESOLVED: 'Resolved',
  VERIFIED: 'Verified',
  CLOSED: 'Closed',
}

export const PRIORITY_LABEL: Record<Priority, string> = { P1: 'Critical', P2: 'High', P3: 'Medium', P4: 'Low' }

export const SLA_LABEL: Record<SlaStatus, string> = {
  NOT_STARTED: 'Not started',
  ON_TRACK: 'On track',
  AT_RISK: 'At risk',
  PAUSED: 'Paused',
  BREACHED: 'Breached',
  MET: 'Met',
}

export const LEVEL_LABEL: Record<Level, string> = { LOW: 'Low', MEDIUM: 'Medium', HIGH: 'High', CRITICAL: 'Critical' }

export const ROLE_LABEL: Record<Role, string> = {
  EMPLOYEE: 'Employee',
  TECHNICIAN: 'Technician',
  TEAM_LEAD: 'Team lead',
  ADMIN: 'Administrator',
  MANAGER: 'IT manager',
}

export function initials(name?: string) {
  if (!name) return '?'
  return name.split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0]!.toUpperCase()).join('')
}

export function humanise(code?: string) {
  if (!code) return ''
  const s = code.replace(/_/g, ' ').toLowerCase()
  return s.charAt(0).toUpperCase() + s.slice(1)
}

export function pct(n?: number | null, digits = 0) {
  if (n === undefined || n === null) return '—'
  return `${n.toFixed(digits)}%`
}
