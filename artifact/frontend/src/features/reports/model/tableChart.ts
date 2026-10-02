/**
 * Turns a markdown table from a report into chart data, when it is one worth charting (KI-4).
 * Report tables are free-form LLM output, so this is deliberately conservative: a table qualifies
 * only when one axis is periods (Q3 FY26, FY2025, Q2'25, 2026-09-28…) and at least one metric has
 * three plain numbers along it. Anything else — text tables, single "Value" columns, mixed units —
 * gets no chart rather than a misleading one.
 */

export interface TableChart {
  /** Period labels, oldest first. */
  periods: string[]
  /** At most eight (one per palette slot); only the first `visible` start switched on. */
  series: { name: string; values: (number | null)[] }[]
  visible: number
  /** The table's corner cell ("($M)", "Metric ($B)"): it often carries the unit. */
  label: string
  /** The unit every series shares, split around the number: `$` + `B`, `` + `%`. */
  unit: { prefix: string; suffix: string }
  /** Numeric metrics left out: their unit differs from the charted ones, or past the series cap. */
  omitted: number
}

interface Cell {
  value: number
  prefix: string
  suffix: string
}

/** One palette slot per series; past four lines a chart stops being readable, so the rest start off. */
const MAX_SERIES = 8
const VISIBLE_SERIES = 4
const MIN_POINTS = 3

/** Drops markdown emphasis and code marks that LLMs put around important cells. */
export function plainText(text: string): string {
  return text
    .replace(/\*\*|__|`/g, '')
    .replace(/(^|\s)[*_]|[*_](\s|$)/g, '$1$2')
    .trim()
}

/**
 * A cell that is one number with an optional unit: `282,836`, `$1,037.35`, `59.5%`, `−5.91`,
 * `**11.34**`, `~994`, `(6.042)`, `+65.5%`, `24.4x`, `$4.171 T`, `%84,6`. Anything more is not a number.
 */
export function parseCell(text: string): Cell | null {
  let s = plainText(text)
    .replace(/[−–]/g, '-')
    .replace(/^[~≈<>]\s*/, '')
  let negative = false
  const parens = /^\((.*)\)$/.exec(s)
  if (parens) {
    negative = true
    s = parens[1]!.trim()
  }
  // Suffix: %, ×, pp, bps, B/M/K/T, bn, mlr $ … — one short unit word, never free text ("96% Yes").
  const match =
    /^([+-])?\s*([$€£%]?)\s*([+-])?(\d[\d,]*(?:\.\d+)?)\s*((?:%|×|\$|[a-zA-Z]{1,4})(?:\s?\$)?)?$/.exec(
      s,
    )
  if (!match) return null
  const [, sign1, prefix = '', sign2, digits = '', suffix = ''] = match
  // "1,234,567" groups thousands; "84,6" (Turkish reports) is a decimal comma.
  const number = /^\d{1,3}(,\d{3})+(\.\d+)?$/.test(digits)
    ? Number(digits.replace(/,/g, ''))
    : /^\d+,\d{1,2}$/.test(digits)
      ? Number(digits.replace(',', '.'))
      : Number(digits)
  if (!Number.isFinite(number)) return null
  const minus = negative || sign1 === '-' || sign2 === '-'
  return { value: minus ? -number : number, prefix, suffix: suffix.trim() }
}

type PeriodKind = 'quarter' | 'fiscal' | 'date' | 'year'

/** A sortable key for a period label, or null when the label is not a period (TTM, Comment…). */
export function parsePeriod(label: string): { kind: PeriodKind; key: number } | null {
  const s = plainText(label)
  const year = (y: string) => (y.length === 2 ? 2000 + Number(y) : Number(y))
  let m = /^Q([1-4])\s*(?:FY)?\s*'?(\d{4}|\d{2})\b/i.exec(s)
  if (m) return { kind: 'quarter', key: year(m[2]!) * 10 + Number(m[1]) }
  m = /^FY\s*'?(\d{4}|\d{2})\b/i.exec(s)
  if (m) return { kind: 'fiscal', key: year(m[1]!) }
  m = /^(\d{4})-(\d{2})(?:-(\d{2}))?\b/.exec(s)
  if (m) return { kind: 'date', key: Date.UTC(Number(m[1]), Number(m[2]) - 1, Number(m[3] ?? 1)) }
  m = /^((?:19|20)\d{2})$/.exec(s)
  if (m) return { kind: 'year', key: Number(m[1]) }
  return null
}

/** Indexes of the labels that are periods of one kind, when there are enough of them. */
function periodIndexes(labels: string[]): number[] {
  const periods = labels.map((label, index) => ({ index, period: parsePeriod(label) }))
  const byKind = new Map<PeriodKind, number[]>()
  for (const { index, period } of periods) {
    if (period) byKind.set(period.kind, [...(byKind.get(period.kind) ?? []), index])
  }
  const best = [...byKind.values()].sort((a, b) => b.length - a.length)[0] ?? []
  return best.length >= MIN_POINTS ? best : []
}

/**
 * Dated rows are a time series only when the other columns are mostly numbers; a log of events
 * (insider trades: date, name, position, shares) is not.
 */
function mostlyNumeric(header: string[], rows: string[][], dated: number[]): boolean {
  const columns = header.slice(1).map((_, j) => dated.some((i) => parseCell(rows[i]![j + 1] ?? '')))
  return columns.filter(Boolean).length * 2 > columns.length
}

export function tableChart(header: string[], rows: string[][]): TableChart | null {
  // Periods across the header (metrics are rows), or down the first column (metrics are columns).
  const across = periodIndexes(header.slice(1)).map((i) => i + 1)
  const down = periodIndexes(rows.map((row) => row[0] ?? ''))
  let labels: string[]
  let candidates: { name: string; cells: (Cell | null)[] }[]
  if (across.length) {
    labels = across.map((i) => header[i]!)
    candidates = rows.map((row) => ({
      name: row[0] ?? '',
      cells: across.map((i) => parseCell(row[i] ?? '')),
    }))
  } else if (down.length && mostlyNumeric(header, rows, down)) {
    labels = down.map((i) => rows[i]![0]!)
    candidates = header.slice(1).map((name, j) => ({
      name,
      cells: down.map((i) => parseCell(rows[i]![j + 1] ?? '')),
    }))
  } else {
    return null
  }

  // A metric qualifies with enough numbers that all share one unit.
  const unitOf = (cell: Cell) => `${cell.prefix}|${cell.suffix.toLowerCase()}`
  const metrics = candidates.flatMap(({ name, cells }) => {
    const present = cells.filter((cell): cell is Cell => cell !== null)
    const units = new Set(present.map(unitOf))
    if (present.length < MIN_POINTS || units.size !== 1) return []
    return [{ name: plainText(name), cells, unit: unitOf(present[0]!), sample: present[0]! }]
  })
  if (!metrics.length) return null

  // Chart the unit most metrics share (the first one wins a tie); the rest stay in the table.
  const counts = new Map<string, number>()
  for (const metric of metrics) counts.set(metric.unit, (counts.get(metric.unit) ?? 0) + 1)
  const unit = [...counts.entries()].sort((a, b) => b[1] - a[1])[0]![0]
  const shared = metrics.filter((metric) => metric.unit === unit)
  const charted = shared.slice(0, MAX_SERIES)

  // Oldest first, whichever way the table was written.
  const order = labels
    .map((label, index) => ({ index, key: parsePeriod(label)!.key }))
    .sort((a, b) => a.key - b.key)
    .map(({ index }) => index)

  return {
    // "Q3 FY26 (05/31/26)" → "Q3 FY26": the period end date only crowds the axis.
    periods: order.map((i) => plainText(labels[i]!).replace(/\s*\([^)]*\)$/, '')),
    visible: Math.min(charted.length, VISIBLE_SERIES),
    label: across.length ? plainText(header[0] ?? '') : '',
    series: charted.map((metric) => ({
      name: metric.name,
      values: order.map((i) => metric.cells[i]?.value ?? null),
    })),
    unit: { prefix: charted[0]!.sample.prefix, suffix: charted[0]!.sample.suffix },
    omitted: metrics.length - charted.length,
  }
}
