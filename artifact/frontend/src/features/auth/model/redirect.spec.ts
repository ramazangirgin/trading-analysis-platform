import { describe, expect, it } from 'vitest'
import { safeRedirect } from './redirect'

describe('safeRedirect', () => {
  it('keeps a path of this app', () => {
    expect(safeRedirect('/reports')).toBe('/reports')
    expect(safeRedirect('/reports/42?tab=prices#top')).toBe('/reports/42?tab=prices#top')
  })

  it('falls back to the start page for anything else', () => {
    expect(safeRedirect('//evil.example')).toBe('/')
    expect(safeRedirect('/\\evil.example')).toBe('/')
    expect(safeRedirect('https://evil.example')).toBe('/')
    expect(safeRedirect('reports')).toBe('/')
    expect(safeRedirect('/login')).toBe('/')
    expect(safeRedirect('/login?redirect=%2Freports')).toBe('/')
    expect(safeRedirect(['/reports'])).toBe('/')
    expect(safeRedirect(undefined)).toBe('/')
    expect(safeRedirect(null)).toBe('/')
  })
})
