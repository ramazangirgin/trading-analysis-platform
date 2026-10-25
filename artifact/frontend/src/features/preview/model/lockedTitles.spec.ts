import { describe, expect, it } from 'vitest'
import { lockedTitleKey } from './lockedTitles'

describe('lockedTitleKey', () => {
  it('prefixes a known section key', () => {
    expect(lockedTitleKey('sentiment_report', () => true)).toBe('preview.locked.sentiment_report')
  })

  it('falls back for a key without a translation', () => {
    expect(lockedTitleKey('brand_new_section', () => false)).toBe('preview.locked.other')
  })
})
