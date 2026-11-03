import { describe, expect, it } from 'vitest'
import { cn, duration, humanise, initials, pct, remaining } from './format'

describe('formatting helpers', () => {
  it('FE-08 durations read naturally in minutes, hours and days', () => {
    expect(duration(45)).toBe('45 min')
    expect(duration(60)).toBe('1 h')
    expect(duration(200)).toBe('3 h 20 min')
    expect(duration(24 * 60 + 4 * 60)).toBe('1 d 4 h')
    expect(duration(null)).toBe('—')
  })

  it('FE-09 SLA time left or overdue', () => {
    expect(remaining(34)).toBe('34 min left')
    expect(remaining(-12)).toBe('12 min overdue')
    expect(remaining(undefined)).toBe('')
  })

  it('FE-10 names, codes and percentages', () => {
    expect(initials('moh bhargava')).toBe('MB')
    expect(initials('Neha  Team Lead')).toBe('NT')
    expect(initials('')).toBe('?')
    expect(humanise('AWAITING_INFO')).toBe('Awaiting info')
    expect(pct(87.456, 1)).toBe('87.5%')
    expect(pct(null)).toBe('—')
    expect(cn('a', false, null, 'b')).toBe('a b')
  })
})
