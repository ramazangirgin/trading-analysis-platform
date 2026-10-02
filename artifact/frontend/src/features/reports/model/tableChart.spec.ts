import { describe, expect, it } from 'vitest'
import { parseCell, parsePeriod, tableChart } from './tableChart'

describe('parseCell', () => {
  it.each([
    ['282,836', 282836, '', ''],
    ['$1,037.35', 1037.35, '$', ''],
    ['59.5%', 59.5, '', '%'],
    ['−5.91', -5.91, '', ''],
    ['**11.34**', 11.34, '', ''],
    ['~994', 994, '', ''],
    ['(6.042)', -6.042, '', ''],
    ['+65.5%', 65.5, '', '%'],
    ['24.4x', 24.4, '', 'x'],
    ['$4.171 T', 4.171, '$', 'T'],
    ['%84,6', 84.6, '%', ''],
  ])('reads %s', (text, value, prefix, suffix) => {
    expect(parseCell(text)).toEqual({ value, prefix, suffix })
  })

  it.each(['—', 'Bullish', '$1,255.00 / $159.97', '96% Yes', 'Q3 FY26', ''])('rejects %s', (text) =>
    expect(parseCell(text)).toBeNull(),
  )
})

describe('parsePeriod', () => {
  it('orders quarters, fiscal years and dates', () => {
    expect(parsePeriod("Q2'25")!.key).toBeLessThan(parsePeriod('Q4 FY25 (08/31/25)')!.key)
    expect(parsePeriod('**Q2 FY26**')!.key).toBeLessThan(parsePeriod('Q3 FY26 (05/31/26)')!.key)
    expect(parsePeriod('FY2025')!.key).toBeLessThan(parsePeriod('FY2026 (1/31/26)')!.key)
    expect(parsePeriod('2026-09-25')!.kind).toBe('date')
  })

  it('knows what is not a period', () => {
    for (const label of ['TTM', 'Metric', 'Comment', 'Value (2026-09-28)', 'YoY']) {
      expect(parsePeriod(label)).toBeNull()
    }
  })
})

describe('tableChart', () => {
  it('charts metrics across period columns, oldest first, skipping TTM', () => {
    const chart = tableChart(
      ['Metric', 'Q3 FY25', 'Q4 FY25', 'Q1 FY26', 'Q2 FY26', 'Q3 FY26', 'TTM'],
      [
        ['Gross Margin', '37.7%', '44.7%', '56.0%', '74.4%', '**84.6%**', '72.6%'],
        ['Net Margin', '20.3%', '28.3%', '38.4%', '57.8%', '**68.1%**', '55.9%'],
      ],
    )
    expect(chart!.periods).toEqual(['Q3 FY25', 'Q4 FY25', 'Q1 FY26', 'Q2 FY26', 'Q3 FY26'])
    expect(chart!.series[0]).toEqual({ name: 'Gross Margin', values: [37.7, 44.7, 56, 74.4, 84.6] })
    expect(chart!.unit).toEqual({ prefix: '', suffix: '%' })
  })

  it('reverses newest-first tables', () => {
    const chart = tableChart(
      ['($B)', 'FY2026 (1/31/26)', 'FY2025', 'FY2024', 'FY2023'],
      [
        ['Revenue', '**215.938**', '130.497', '60.922', '26.974'],
        ['YoY Growth', '**+65.5%**', '+114.2%', '+125.9%', '—'],
        ['CapEx', '(6.042)', '(3.236)', '(1.069)', '(1.833)'],
      ],
    )
    expect(chart!.periods).toEqual(['FY2023', 'FY2024', 'FY2025', 'FY2026'])
    // Revenue and CapEx share a unit; growth (%) is left to the table.
    expect(chart!.series.map((s) => s.name)).toEqual(['Revenue', 'CapEx'])
    expect(chart!.series[1]!.values).toEqual([-1.833, -1.069, -3.236, -6.042])
    expect(chart!.omitted).toBe(1)
  })

  it('charts dated rows with metric columns', () => {
    const chart = tableChart(
      ['Date', 'MACD', 'Signal', 'Histogram'],
      [
        ['2026-09-28', '34.52', '25.65', '8.87'],
        ['2026-09-25', '35.14', '23.43', '11.71'],
        ['2026-09-24', '33.02', '20.51', '12.51'],
      ],
    )
    expect(chart!.periods).toEqual(['2026-09-24', '2026-09-25', '2026-09-28'])
    expect(chart!.series.map((s) => s.name)).toEqual(['MACD', 'Signal', 'Histogram'])
  })

  it('keeps up to eight metrics and switches on the first four', () => {
    const rows = Array.from({ length: 10 }, (_, i) => [`M${i}`, '1', '2', '3'])
    const chart = tableChart(['($M)', 'FY2023', 'FY2024', 'FY2025'], rows)
    expect(chart!.series).toHaveLength(8)
    expect(chart!.visible).toBe(4)
    expect(chart!.omitted).toBe(2)
    expect(chart!.label).toBe('($M)')
  })

  it('does not chart a dated log of events', () => {
    const chart = tableChart(
      ['Date', 'Insider', 'Position', 'Shares'],
      [
        ['2026-09-01', 'A. Person', 'CFO', '1,000'],
        ['2026-08-01', 'B. Person', 'Director', '2,000'],
        ['2026-07-01', 'C. Person', 'CEO', '3,000'],
      ],
    )
    expect(chart).toBeNull()
  })

  it('leaves text and single-value tables alone', () => {
    expect(
      tableChart(
        ['Metric', 'Value', 'Comment'],
        [
          ['Market Cap', '$4.171 T', 'Mega-cap'],
          ['PE (TTM)', '17.12', 'Distorted'],
          ['PEG', '0.16', 'Cheap'],
        ],
      ),
    ).toBeNull()
    expect(
      tableChart(
        ['Market', 'Implied prob.', 'Volume'],
        [
          ['MU beats', '**96% Yes**', '$5,321'],
          ['CapEx', '96% Yes', '$1,081'],
          ['Shipment', '97% Yes', '$974'],
        ],
      ),
    ).toBeNull()
  })
})
