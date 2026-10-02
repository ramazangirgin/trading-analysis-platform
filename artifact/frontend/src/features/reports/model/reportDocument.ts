import MarkdownIt from 'markdown-it'
import type { Analysis, AnalysisReport, Rating } from '@/shared/api/types'
import { SECTIONS } from './sections'

/** One ticker and trade date with a report in the data dir; `id` is its newest finished analysis. */
export interface ReportIndexEntry {
  id: string
  ticker: string
  tradeDate: string
  rating: Rating | null
  runs: number
}

/**
 * The data dir keeps one report per ticker and date (upstream overwrites the files), so the
 * index lists each once, through the newest analysis that finished it.
 */
export function reportIndex(analyses: readonly Analysis[]): ReportIndexEntry[] {
  const byKey = new Map<string, { newest: Analysis; runs: number }>()
  for (const analysis of analyses) {
    if (analysis.status !== 'COMPLETED') continue
    const key = `${analysis.spec.ticker}|${analysis.spec.tradeDate}`
    const seen = byKey.get(key)
    if (!seen) byKey.set(key, { newest: analysis, runs: 1 })
    else {
      seen.runs++
      if (analysis.createdAt > seen.newest.createdAt) seen.newest = analysis
    }
  }
  return [...byKey.values()]
    .map(({ newest, runs }) => ({
      id: newest.id,
      ticker: newest.spec.ticker,
      tradeDate: newest.spec.tradeDate,
      rating: newest.rating,
      runs,
    }))
    .sort((a, b) => b.tradeDate.localeCompare(a.tradeDate) || a.ticker.localeCompare(b.ticker))
}

export type DebateKind = 'investment' | 'risk'

/** A section (`sections.<key>`) or a debate turn (`speakers.<key>`) of a report, in reading order. */
export interface ReportPart {
  key: string
  kind: 'section' | 'debate'
  debate?: DebateKind
  markdown: string
}

const DEBATES: Record<DebateKind, readonly string[]> = {
  investment: ['bull', 'bear', 'research_judge'],
  risk: ['aggressive', 'conservative', 'neutral', 'risk_judge'],
}

export function reportParts(report: Pick<AnalysisReport, 'sections' | 'debates'>): ReportPart[] {
  const parts: ReportPart[] = SECTIONS.filter((key) => report.sections[key]?.trim()).map((key) => ({
    key,
    kind: 'section',
    markdown: report.sections[key]!,
  }))
  for (const debate of ['investment', 'risk'] as const) {
    for (const speaker of DEBATES[debate]) {
      const markdown = report.debates[speaker]
      if (markdown?.trim()) parts.push({ key: speaker, kind: 'debate', debate, markdown })
    }
  }
  return parts
}

export interface Heading {
  level: number
  text: string
}

const parser = new MarkdownIt({ html: false })

/** A part's own h1-h3 headings, in the order they are rendered (for the table of contents). */
export function headings(markdown: string): Heading[] {
  const tokens = parser.parse(markdown, {})
  const found: Heading[] = []
  tokens.forEach((token, index) => {
    const level = Number(token.tag.slice(1))
    if (token.type !== 'heading_open' || level > 3) return
    const inline = tokens[index + 1]
    const text = (inline?.children ?? [])
      .filter((child) => child.type === 'text' || child.type === 'code_inline')
      .map((child) => child.content)
      .join('')
      .trim()
    if (text) found.push({ level, text })
  })
  return found
}

/**
 * Pushes a part's own headings down under the export's `## part` heading, leaving code blocks
 * alone. `# Title` becomes `### Title` with `by = 2`; nothing goes below h6.
 */
export function demoteHeadings(markdown: string, by: number): string {
  let fenced = false
  return markdown
    .split('\n')
    .map((line) => {
      if (/^\s*(```|~~~)/.test(line)) fenced = !fenced
      if (fenced) return line
      return line.replace(/^(#{1,6})(?=\s)/, (hashes) =>
        '#'.repeat(Math.min(6, hashes.length + by)),
      )
    })
    .join('\n')
}

/** What an export holds, already in the viewer's words: titles are translated by the caller. */
export interface ExportDocument {
  title: string
  subtitle: string
  parts: { heading: string; group?: string; markdown: string }[]
}

export function exportMarkdown(doc: ExportDocument): string {
  const lines = [`# ${doc.title}`, '', `_${doc.subtitle}_`, '']
  let group: string | undefined
  for (const part of doc.parts) {
    if (part.group && part.group !== group) lines.push(`## ${part.group}`, '')
    group = part.group
    const level = part.group ? '###' : '##'
    lines.push(
      `${level} ${part.heading}`,
      '',
      demoteHeadings(part.markdown.trim(), part.group ? 3 : 2),
      '',
    )
  }
  return lines.join('\n')
}

const escapeHtml = (text: string) =>
  text.replace(
    /[&<>"']/g,
    (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]!,
  )

/**
 * A standalone page (no scripts, no external files) that reads and prints well. `render` turns
 * markdown into sanitized HTML, the same way the reader does.
 */
export function exportHtml(
  doc: ExportDocument,
  render: (markdown: string) => string,
  lang: string,
): string {
  const body = render(exportMarkdown(doc))
  return `<!doctype html>
<html lang="${escapeHtml(lang)}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${escapeHtml(doc.title)}</title>
<style>
  :root { color-scheme: light; }
  body { margin: 0 auto; max-width: 860px; padding: 32px 20px; background: #fff; color: #1f2328;
         font: 15px/1.6 -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; overflow-wrap: anywhere; }
  h1 { font-size: 1.8em; margin: 0 0 4px; }
  h1 + p em { color: #59636e; font-style: normal; }
  h2 { font-size: 1.4em; margin-top: 2em; padding-bottom: 4px; border-bottom: 1px solid #d1d9e0; }
  h3 { font-size: 1.15em; margin-top: 1.6em; }
  h4, h5, h6 { font-size: 1em; }
  table { border-collapse: collapse; margin: 12px 0; display: block; overflow-x: auto; }
  th, td { border: 1px solid #d1d9e0; padding: 4px 8px; }
  th { background: #f6f8fa; }
  pre, code { background: #f6f8fa; border-radius: 4px; }
  pre { padding: 8px 12px; overflow-x: auto; }
  blockquote { margin: 0; padding-left: 12px; border-left: 3px solid #d1d9e0; color: #59636e; }
  @media print {
    body { max-width: none; padding: 0; font-size: 11pt; }
    h2 { break-before: page; }
    h2:first-of-type { break-before: auto; }
    h2, h3, h4 { break-after: avoid; }
    table, pre, blockquote { break-inside: avoid; }
    table { display: table; overflow: visible; }
  }
</style>
</head>
<body>
${body}
</body>
</html>
`
}
