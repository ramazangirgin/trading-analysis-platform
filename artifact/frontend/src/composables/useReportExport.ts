import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import { useI18n } from 'vue-i18n'
import type { Analysis } from '@/api/client'
import {
  exportHtml,
  exportMarkdown,
  type ExportDocument,
  type ReportPart,
} from '@/domain/reportDocument'
import { localizeReport } from '@/domain/reportLanguage'

const markdown = new MarkdownIt({ html: false, linkify: true })
const render = (source: string) => DOMPurify.sanitize(markdown.render(source))

/**
 * Saves a report as Markdown or a standalone HTML page, or prints it (the browser's print dialog
 * offers "Save as PDF"). Built in the browser so the export reads exactly like the reader: same
 * rendering, same sanitizing, upstream's fixed English labels in the report's language.
 */
export function useReportExport() {
  const { t, locale } = useI18n()

  function document(
    analysis: Analysis,
    parts: ReportPart[],
    language: string | null,
  ): ExportDocument {
    const spec = analysis.spec
    const rating = analysis.rating ? t(`rating.${analysis.rating}`) : '—'
    return {
      title: `${spec.ticker} — ${spec.tradeDate}`,
      subtitle: [
        `${t('reports.rating')}: ${rating}`,
        `${spec.llmProvider} · ${spec.deepThinkLlm} / ${spec.quickThinkLlm}`,
        `${t('reports.exported')} ${new Date().toLocaleString(locale.value)}`,
      ].join(' · '),
      parts: parts.map((part) => ({
        heading: t(part.kind === 'section' ? `sections.${part.key}` : `speakers.${part.key}`),
        group:
          part.debate === 'investment'
            ? t('detail.investmentDebate')
            : part.debate === 'risk'
              ? t('detail.riskDebate')
              : undefined,
        markdown: localizeReport(part.markdown, language),
      })),
    }
  }

  const baseName = (analysis: Analysis) =>
    `${analysis.spec.ticker}_${analysis.spec.tradeDate}_report`

  function save(filename: string, content: string, type: string) {
    const url = URL.createObjectURL(new Blob([content], { type }))
    const link = window.document.createElement('a')
    link.href = url
    link.download = filename
    link.click()
    setTimeout(() => URL.revokeObjectURL(url), 1_000)
  }

  function saveMarkdown(analysis: Analysis, parts: ReportPart[], language: string | null) {
    save(
      `${baseName(analysis)}.md`,
      exportMarkdown(document(analysis, parts, language)),
      'text/markdown;charset=utf-8',
    )
  }

  const html = (analysis: Analysis, parts: ReportPart[], language: string | null) =>
    exportHtml(document(analysis, parts, language), render, language === 'Turkish' ? 'tr' : 'en')

  function saveHtml(analysis: Analysis, parts: ReportPart[], language: string | null) {
    save(`${baseName(analysis)}.html`, html(analysis, parts, language), 'text/html;charset=utf-8')
  }

  /** Prints the HTML export from a hidden frame; the print dialog saves it as PDF. */
  function print(analysis: Analysis, parts: ReportPart[], language: string | null) {
    const frame = window.document.createElement('iframe')
    frame.style.cssText = 'position:fixed;width:0;height:0;border:0;visibility:hidden'
    frame.srcdoc = html(analysis, parts, language)
    frame.onload = () => {
      frame.contentWindow?.focus()
      frame.contentWindow?.print()
      setTimeout(() => frame.remove(), 60_000)
    }
    window.document.body.appendChild(frame)
  }

  return { saveMarkdown, saveHtml, print }
}
