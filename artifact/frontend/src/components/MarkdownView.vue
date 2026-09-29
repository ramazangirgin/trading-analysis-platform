<script setup lang="ts">
import { computed, defineAsyncComponent } from 'vue'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import { localizeReport } from '@/domain/reportLanguage'
import { tableChart, type TableChart } from '@/domain/tableChart'

// ECharts is only downloaded for a report that has a chartable table.
const ReportTableChart = defineAsyncComponent(() => import('./ReportTableChart.vue'))

/**
 * Renders LLM-written markdown. The output is untrusted input, so the HTML is sanitized.
 * With `charts`, a table that is a series over periods gets a chart beside it (KI-4).
 */
const props = defineProps<{ source: string; language?: string | null; charts?: boolean }>()

const markdown = new MarkdownIt({ html: false, linkify: true, breaks: false })
const render = (source: string) => DOMPurify.sanitize(markdown.render(source))

interface Part {
  html: string
  chart?: TableChart
}

/** Top-level tables with their cells as written (header, then body rows) and source lines. */
function tables(source: string) {
  const found: { lines: [number, number]; header: string[]; rows: string[][] }[] = []
  let current: (typeof found)[number] | null = null
  let row: string[] = []
  let inHead = false
  for (const token of markdown.parse(source, {})) {
    if (token.type === 'table_open' && token.level === 0 && token.map) {
      current = { lines: [token.map[0], token.map[1]], header: [], rows: [] }
    } else if (!current) {
      continue
    } else if (token.type === 'thead_open' || token.type === 'thead_close') {
      inHead = token.type === 'thead_open'
    } else if (token.type === 'tr_open') {
      row = []
    } else if (token.type === 'inline') {
      row.push(token.content)
    } else if (token.type === 'tr_close') {
      if (inHead) current.header = row
      else current.rows.push(row)
    } else if (token.type === 'table_close') {
      found.push(current)
      current = null
    }
  }
  return found
}

const parts = computed<Part[]>(() => {
  const source = localizeReport(props.source, props.language)
  if (!props.charts) return [{ html: render(source) }]
  const lines = source.split('\n')
  const result: Part[] = []
  let cursor = 0
  for (const table of tables(source)) {
    const chart = tableChart(table.header, table.rows)
    if (!chart) continue
    const [start, end] = table.lines
    if (start > cursor) result.push({ html: render(lines.slice(cursor, start).join('\n')) })
    result.push({ html: render(lines.slice(start, end).join('\n')), chart })
    cursor = end
  }
  if (cursor < lines.length) result.push({ html: render(lines.slice(cursor).join('\n')) })
  return result
})
</script>

<template>
  <article class="markdown">
    <template v-for="(part, index) in parts" :key="index">
      <div v-if="part.chart" class="markdown__charted">
        <!-- eslint-disable-next-line vue/no-v-html -- sanitized with DOMPurify above -->
        <div class="markdown__table" v-html="part.html" />
        <ReportTableChart :chart="part.chart" class="markdown__chart" />
      </div>
      <!-- eslint-disable-next-line vue/no-v-html -- sanitized with DOMPurify above -->
      <div v-else v-html="part.html" />
    </template>
  </article>
</template>

<style scoped>
.markdown {
  line-height: 1.6;
  overflow-wrap: anywhere;
}

.markdown :deep(table) {
  border-collapse: collapse;
  display: block;
  overflow-x: auto;
  margin: 12px 0;
}

.markdown :deep(th),
.markdown :deep(td) {
  border: 1px solid var(--n-border-color, rgba(128, 128, 128, 0.35));
  padding: 4px 8px;
}

.markdown :deep(pre) {
  overflow-x: auto;
}

/* The table and its chart side by side; the chart drops below when there is no room. */
.markdown__charted {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  column-gap: 24px;
}

.markdown__table {
  flex: 0 1 auto;
  min-width: 0;
  max-width: 100%;
}

/* Numbers and period headers stay on one line; a wide table scrolls instead. */
.markdown__table :deep(th),
.markdown__table :deep(td) {
  overflow-wrap: normal;
  white-space: nowrap;
}

.markdown__chart {
  flex: 1 1 320px;
  min-width: 280px;
}
</style>
