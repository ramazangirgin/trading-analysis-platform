// Public API of the reports feature: report text, prices, export and import of existing runs.
// Pages and other features import from here only (doc/coding-convention/frontend-folder-structure.md).
import { defineAsyncComponent } from 'vue'

export { reportsApi } from './api'
export { default as MarkdownView } from './components/MarkdownView.vue'
/** Loaded on first use: it pulls in ECharts. */
export const PriceChart = defineAsyncComponent(() => import('./components/PriceChart.vue'))
export { useReportExport } from './composables/useReportExport'
export { headings, reportIndex, reportParts, type ReportPart } from './model/reportDocument'
export { reportLanguage } from './model/reportLanguage'
export { SECTIONS } from './model/sections'
