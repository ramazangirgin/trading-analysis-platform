import { describe, expect, it } from 'vitest'
import { excerptText } from './excerptText'

describe('excerptText', () => {
  it('drops heading marks and table syntax', () => {
    const markdown =
      '# Market Analysis\n\nPrice is in an uptrend.\n\n| Indicator | Value | Signal |\n|---|---|---|\n| Close | 182.4 |'
    expect(excerptText(markdown)).toBe(
      'Market Analysis Price is in an uptrend. Indicator Value Signal Close 182.4',
    )
  })

  it('drops emphasis, code, list marks and link targets', () => {
    expect(excerptText('- **Strong** and _soft_ `code`\n> see [the docs](https://x.y)')).toBe(
      'Strong and soft code see the docs',
    )
  })

  it('keeps plain text', () => {
    expect(excerptText('The start of the decision')).toBe('The start of the decision')
  })
})
