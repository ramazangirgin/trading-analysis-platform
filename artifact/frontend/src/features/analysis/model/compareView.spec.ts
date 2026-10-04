import { describe, expect, it } from 'vitest'
import type { Analysis, AnalysisReport } from '@/shared/api/types'
import { compareView, type CompareRun } from './compareView'

const SECTIONS = ['market_report', 'news_report', 'final_trade_decision'] as const

function analysis(id: string, overrides: Partial<Analysis['spec']> = {}): Analysis {
  return {
    id,
    status: 'COMPLETED',
    source: 'PLATFORM',
    rating: 'Overweight',
    decision: 'Buy a little.',
    spec: {
      ticker: 'NVDA',
      tradeDate: '2026-09-25',
      assetType: 'STOCK',
      analysts: ['MARKET', 'NEWS'],
      llmProvider: 'openai',
      deepThinkLlm: 'gpt-5.4',
      quickThinkLlm: 'gpt-5.4-mini',
      maxDebateRounds: 1,
      maxRiskDiscussRounds: 1,
      outputLanguage: 'English',
      checkpointEnabled: false,
      ...overrides,
    },
    stats: {
      llmCalls: 10,
      toolCalls: 4,
      tokensIn: 1000,
      tokensOut: 500,
      costUsd: 0.12,
      elapsedMs: 9000,
    },
  } as unknown as Analysis // only the fields compareView reads
}

const report = (sections: Record<string, string>) =>
  ({ sections, debates: {} }) as unknown as AnalysisReport

const run = (id: string, overrides = {}, sections?: Record<string, string>): CompareRun => ({
  analysis: analysis(id, overrides),
  report: sections ? report(sections) : null,
})

const find = (rows: ReturnType<typeof compareView>, labelKey: string) =>
  rows.find((r) => r.labelKey === labelKey)!

describe('compareView', () => {
  it('builds spec, decision and stats rows with one value per run', () => {
    const rows = compareView([run('a'), run('b', { ticker: 'AMD' })], SECTIONS)

    expect(find(rows, 'compare.ticker')).toMatchObject({ values: ['NVDA', 'AMD'], group: 'spec' })
    expect(find(rows, 'compare.analysts').values).toEqual(['MARKET, NEWS', 'MARKET, NEWS'])
    expect(find(rows, 'compare.rating')).toMatchObject({ values: ['Overweight', 'Overweight'] })
    expect(find(rows, 'detail.cost')).toMatchObject({ values: [0.12, 0.12], format: 'usd' })
    expect(find(rows, 'detail.elapsed').group).toBe('stats')
  })

  it('works for three runs', () => {
    const rows = compareView([run('a'), run('b'), run('c', { deepThinkLlm: 'o3' })], SECTIONS)

    expect(find(rows, 'compare.deepModel').values).toEqual(['gpt-5.4', 'gpt-5.4', 'o3'])
  })

  it('flags only the rows whose values differ', () => {
    const rows = compareView([run('a'), run('b', { deepThinkLlm: 'o3' })], SECTIONS)

    expect(find(rows, 'compare.deepModel').differs).toBe(true)
    expect(find(rows, 'compare.quickModel').differs).toBe(false)
    expect(find(rows, 'compare.ticker').differs).toBe(false)
  })

  it('keeps a run without a report and shows no text for it', () => {
    const rows = compareView([run('a', {}, { market_report: '# M' }), run('b')], SECTIONS)

    const market = find(rows, 'sections.market_report')
    expect(market.values).toEqual(['# M', null])
    expect(market.differs).toBe(true)
  })

  it('lists sections in the given order and skips those no run has', () => {
    const rows = compareView(
      [
        run('a', {}, { final_trade_decision: 'F', market_report: 'M' }),
        run('b', {}, { market_report: 'M2' }),
      ],
      SECTIONS,
    )

    const sections = rows.filter((r) => r.group === 'section').map((r) => r.labelKey)
    expect(sections).toEqual(['sections.market_report', 'sections.final_trade_decision'])
  })

  it('shows a section present in one run only as missing in the other', () => {
    const rows = compareView([run('a', {}, { news_report: 'N' }), run('b', {}, {})], SECTIONS)

    expect(find(rows, 'sections.news_report').values).toEqual(['N', null])
  })
})
