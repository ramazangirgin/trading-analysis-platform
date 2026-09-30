import { describe, expect, it } from 'vitest'
import MarkdownIt from 'markdown-it'
import type { Analysis } from '@/api/client'
import {
  demoteHeadings,
  exportHtml,
  exportMarkdown,
  headings,
  reportIndex,
  reportParts,
  type ExportDocument,
} from './reportDocument'

const analysis = (
  id: string,
  ticker: string,
  tradeDate: string,
  createdAt: string,
  status: Analysis['status'] = 'COMPLETED',
): Analysis =>
  ({
    id,
    status,
    createdAt,
    rating: 'HOLD',
    spec: { ticker, tradeDate },
  }) as unknown as Analysis

describe('reportIndex', () => {
  it('lists each ticker and date once, through its newest finished analysis', () => {
    const index = reportIndex([
      analysis('a', 'MU', '2026-09-29', '2026-09-29T10:00:00Z'),
      analysis('b', 'MU', '2026-09-29', '2026-09-29T21:00:00Z'),
      analysis('c', 'MU', '2026-09-29', '2026-09-29T22:00:00Z', 'FAILED'),
      analysis('d', 'AMD', '2026-09-29', '2026-09-29T09:00:00Z'),
      analysis('e', 'NVDA', '2026-09-27', '2026-09-27T21:00:00Z'),
      analysis('f', 'GOOG', '2026-09-28', '2026-09-28T09:00:00Z', 'RUNNING'),
    ])

    expect(
      index.map((entry) => `${entry.ticker}/${entry.tradeDate}/${entry.id}/${entry.runs}`),
    ).toEqual(['AMD/2026-09-29/d/1', 'MU/2026-09-29/b/2', 'NVDA/2026-09-27/e/1'])
  })
})

describe('reportParts', () => {
  it('orders sections by the pipeline, then each debate by its speakers', () => {
    const parts = reportParts({
      sections: { final_trade_decision: 'Hold', market_report: '# Market', news_report: '  ' },
      debates: { risk_judge: 'final', bull: 'up', bear: 'down', aggressive: 'go' },
    })

    expect(parts.map((part) => `${part.kind}:${part.key}:${part.debate ?? ''}`)).toEqual([
      'section:market_report:',
      'section:final_trade_decision:',
      'debate:bull:investment',
      'debate:bear:investment',
      'debate:aggressive:risk',
      'debate:risk_judge:risk',
    ])
  })
})

describe('headings', () => {
  it('finds h1 to h3 with their plain text', () => {
    expect(
      headings('# MU — **Technical** view\n\ntext\n\n## 1. `MACD`\n\n#### too deep\n\n### Risks'),
    ).toEqual([
      { level: 1, text: 'MU — Technical view' },
      { level: 2, text: '1. MACD' },
      { level: 3, text: 'Risks' },
    ])
  })
})

describe('demoteHeadings', () => {
  it('pushes headings down, not code or #hashtags', () => {
    expect(demoteHeadings('# A\n#tag\n```\n# code\n```\n###### deep', 2)).toBe(
      '### A\n#tag\n```\n# code\n```\n###### deep',
    )
  })
})

const doc: ExportDocument = {
  title: 'MU — 2026-09-29',
  subtitle: 'Rating: Hold',
  parts: [
    { heading: 'Market report', markdown: '# Technicals\n\n| a | b |\n|---|---|\n| 1 | 2 |' },
    { heading: 'Bull', group: 'Investment debate', markdown: 'Buy <script>alert(1)</script>' },
    { heading: 'Bear', group: 'Investment debate', markdown: 'Wait' },
  ],
}

describe('exportMarkdown', () => {
  it('nests every part under the report title', () => {
    expect(exportMarkdown(doc)).toBe(
      [
        '# MU — 2026-09-29',
        '',
        '_Rating: Hold_',
        '',
        '## Market report',
        '',
        '### Technicals\n\n| a | b |\n|---|---|\n| 1 | 2 |',
        '',
        '## Investment debate',
        '',
        '### Bull',
        '',
        'Buy <script>alert(1)</script>',
        '',
        '### Bear',
        '',
        'Wait',
        '',
      ].join('\n'),
    )
  })
})

describe('exportHtml', () => {
  it('is a standalone page with the rendered, escaped report', () => {
    const markdown = new MarkdownIt({ html: false })
    const html = exportHtml({ ...doc, title: 'MU <b>' }, (source) => markdown.render(source), 'tr')

    expect(html).toMatch(/^<!doctype html>/)
    expect(html).toContain('<html lang="tr">')
    expect(html).toContain('<title>MU &lt;b&gt;</title>')
    expect(html).toContain('<h3>Technicals</h3>')
    expect(html).toContain('<table>')
    expect(html).not.toContain('<script>')
    expect(html).not.toMatch(/<link|src=/)
  })
})
