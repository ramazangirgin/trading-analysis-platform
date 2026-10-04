import type { Analysis, AnalysisReport } from '@/shared/api/types'

/** One run of the comparison; `report` is null for a run without one (failed, still running). */
export interface CompareRun {
  analysis: Analysis
  report: AnalysisReport | null
}

/** A column of the compare page: a loaded run, or the reason it could not be loaded. */
export interface CompareColumn {
  id: string
  run: CompareRun | null
  error: string | null
}

/** How the page shows a row's values. */
export type CompareFormat = 'text' | 'rating' | 'integer' | 'usd' | 'duration' | 'markdown'

export interface CompareRow {
  group: 'spec' | 'decision' | 'stats' | 'section'
  /** Message key of the row label. */
  labelKey: string
  format: CompareFormat
  /** One value per run, in the order of the runs; null where the run has none. */
  values: (string | number | null)[]
  /** The runs do not all have the same value, so the page highlights the row. */
  differs: boolean
}

function row(
  group: CompareRow['group'],
  labelKey: string,
  format: CompareFormat,
  values: CompareRow['values'],
): CompareRow {
  return { group, labelKey, format, values, differs: values.some((value) => value !== values[0]) }
}

/**
 * The rows of a side-by-side comparison: the spec, the decision, the stats and, in the given order,
 * the report sections that at least one run has.
 */
export function compareView(runs: CompareRun[], sections: readonly string[]): CompareRow[] {
  const specs = runs.map((run) => run.analysis.spec)
  const stats = runs.map((run) => run.analysis.stats)
  const rows: CompareRow[] = [
    row(
      'spec',
      'compare.ticker',
      'text',
      specs.map((spec) => spec.ticker),
    ),
    row(
      'spec',
      'compare.tradeDate',
      'text',
      specs.map((spec) => spec.tradeDate),
    ),
    row(
      'spec',
      'compare.provider',
      'text',
      specs.map((spec) => spec.llmProvider),
    ),
    row(
      'spec',
      'compare.deepModel',
      'text',
      specs.map((spec) => spec.deepThinkLlm),
    ),
    row(
      'spec',
      'compare.quickModel',
      'text',
      specs.map((spec) => spec.quickThinkLlm),
    ),
    row(
      'spec',
      'compare.analysts',
      'text',
      specs.map((spec) => spec.analysts.join(', ')),
    ),
    row(
      'spec',
      'compare.debateRounds',
      'integer',
      specs.map((spec) => spec.maxDebateRounds),
    ),
    row(
      'spec',
      'compare.riskRounds',
      'integer',
      specs.map((spec) => spec.maxRiskDiscussRounds),
    ),
    row(
      'decision',
      'compare.rating',
      'rating',
      runs.map((run) => run.analysis.rating),
    ),
    row(
      'decision',
      'compare.decision',
      'markdown',
      runs.map((run) => run.analysis.decision),
    ),
    row(
      'stats',
      'detail.llmCalls',
      'integer',
      stats.map((s) => s.llmCalls),
    ),
    row(
      'stats',
      'detail.toolCalls',
      'integer',
      stats.map((s) => s.toolCalls),
    ),
    row(
      'stats',
      'detail.tokensIn',
      'integer',
      stats.map((s) => s.tokensIn),
    ),
    row(
      'stats',
      'detail.tokensOut',
      'integer',
      stats.map((s) => s.tokensOut),
    ),
    row(
      'stats',
      'detail.cost',
      'usd',
      stats.map((s) => s.costUsd),
    ),
    row(
      'stats',
      'detail.elapsed',
      'duration',
      stats.map((s) => s.elapsedMs),
    ),
  ]
  for (const key of sections) {
    const texts = runs.map((run) => run.report?.sections[key]?.trim() || null)
    if (texts.some((text) => text !== null)) {
      rows.push(row('section', `sections.${key}`, 'markdown', texts))
    }
  }
  return rows
}
