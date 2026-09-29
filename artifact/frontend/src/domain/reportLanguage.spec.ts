import { describe, expect, it } from 'vitest'
import { localizeReport } from './reportLanguage'

describe('localizeReport', () => {
  it('translates upstream labels and ratings in a Turkish report', () => {
    const source = [
      '**Rating**: Underweight',
      '',
      '**Executive Summary**: MU için pozisyon azaltılmalı; yön net: Underweight.',
      '**Price Target**: not provided',
      '**Time Horizon**: 1-3 ay',
    ].join('\n')

    expect(localizeReport(source, 'Turkish')).toBe(
      [
        '**Karar**: Ağırlık Azalt',
        '',
        '**Yönetici Özeti**: MU için pozisyon azaltılmalı; yön net: Ağırlık Azalt.',
        '**Hedef Fiyat**: belirtilmedi',
        '**Zaman Ufku**: 1-3 ay',
      ].join('\n'),
    )
  })

  it('drops the debate prefixes upstream adds', () => {
    expect(localizeReport('Bull Analyst: Değerli jüri...', 'Turkish')).toBe('Değerli jüri...')
  })

  it('leaves English reports alone', () => {
    const english = '**Rating**: Overweight\n\n**Executive Summary**: Build gradually.'
    expect(localizeReport(english, 'English')).toBe(english)
  })
})
