<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  NAlert,
  NButton,
  NButtonGroup,
  NCollapse,
  NCollapseItem,
  NEmpty,
  NInput,
  NSelect,
  NSpin,
  NTag,
} from 'naive-ui'
import type { Analysis, AnalysisReport } from '@/shared/api/types'
import {
  headings,
  MarkdownView,
  PriceChart,
  reportIndex,
  reportLanguage,
  type ReportPart,
  reportParts,
  reportsApi,
  useReportExport,
} from '@/features/reports'
import { useLabels } from '@/shared/composables/useLabels'
import RatingTag from '@/shared/ui/RatingTag.vue'
import { analysisApi } from '@/features/analysis'

// ECharts is loaded only when a market report is open.

const route = useRoute()
const router = useRouter()
const { t, language: languageLabel, error: errorLabel } = useLabels()
const exporter = useReportExport()

const analyses = ref<Analysis[]>([])
const loadingIndex = ref(true)
const failure = ref<unknown>(null)
const search = ref('')
const report = ref<AnalysisReport | null>(null)
const loadingReport = ref(false)
const reportFailure = ref<unknown>(null)
const reader = ref<HTMLElement | null>(null)

const index = computed(() => reportIndex(analyses.value))
const filtered = computed(() => {
  const query = search.value.trim().toUpperCase()
  return query ? index.value.filter((entry) => entry.ticker.includes(query)) : index.value
})
const indexOptions = computed(() =>
  filtered.value.map((entry) => ({
    label: `${entry.ticker} · ${entry.tradeDate}`,
    value: entry.id,
  })),
)

const selectedId = computed(() => (route.params.id ? String(route.params.id) : null))
const selected = computed(() => analyses.value.find((a) => a.id === selectedId.value) ?? null)
const entry = computed(() => index.value.find((e) => e.id === selectedId.value) ?? null)

const parts = computed<ReportPart[]>(() => (report.value ? reportParts(report.value) : []))
const language = computed(() =>
  selected.value
    ? reportLanguage(
        selected.value.spec.outputLanguage,
        selected.value.source,
        report.value?.sections.final_trade_decision ?? selected.value.decision ?? undefined,
      )
    : null,
)

interface TocItem {
  part: ReportPart
  title: string
  headings: { level: number; text: string; index: number }[]
}

const partTitle = (part: ReportPart) =>
  t(part.kind === 'section' ? `sections.${part.key}` : `speakers.${part.key}`)

const toc = computed(() => {
  const groups: { title: string | null; items: TocItem[] }[] = []
  for (const part of parts.value) {
    const group =
      part.debate === 'investment'
        ? t('detail.investmentDebate')
        : part.debate === 'risk'
          ? t('detail.riskDebate')
          : null
    if (!groups.length || groups.at(-1)!.title !== group) groups.push({ title: group, items: [] })
    groups.at(-1)!.items.push({
      part,
      title: partTitle(part),
      // Debate turns are long transcripts; their inner headings would crowd the contents.
      headings:
        part.kind === 'section'
          ? headings(part.markdown).map((heading, i) => ({ ...heading, index: i }))
          : [],
    })
  }
  return groups
})

async function loadIndex() {
  loadingIndex.value = true
  try {
    analyses.value = await analysisApi.listAnalyses({ status: 'COMPLETED' })
    failure.value = null
    if (!selectedId.value && index.value.length) {
      await router.replace({ name: 'report', params: { id: index.value[0]!.id } })
    }
  } catch (e) {
    failure.value = e
  } finally {
    loadingIndex.value = false
  }
}

async function loadReport(id: string | null) {
  report.value = null
  reportFailure.value = null
  if (!id) return
  loadingReport.value = true
  try {
    report.value = await reportsApi.getReport(id)
    reader.value?.scrollTo({ top: 0 })
  } catch (e) {
    reportFailure.value = e
  } finally {
    loadingReport.value = false
  }
}

function open(id: string | null) {
  if (id && id !== selectedId.value) void router.push({ name: 'report', params: { id } })
}

/** Scrolls the reader to a part, or to one of its headings (by position among h1-h3). */
async function jump(key: string, headingIndex?: number) {
  await nextTick()
  const part = reader.value?.querySelector<HTMLElement>(`[data-part="${key}"]`)
  const target =
    headingIndex == null
      ? part
      : part?.querySelectorAll<HTMLElement>('.markdown h1, .markdown h2, .markdown h3')[
          headingIndex
        ]
  // Not smooth: charts rendering during the animation cut it short.
  target?.scrollIntoView({ block: 'start' })
}

function exportAs(kind: 'md' | 'html' | 'pdf') {
  if (!selected.value) return
  const args = [selected.value, parts.value, language.value] as const
  if (kind === 'md') exporter.saveMarkdown(...args)
  else if (kind === 'html') exporter.saveHtml(...args)
  else exporter.print(...args)
}

watch(selectedId, (id) => void loadReport(id), { immediate: true })
onMounted(loadIndex)
</script>

<template>
  <div class="reports">
    <!-- Index: every ticker and date with a report. -->
    <aside class="reports__index">
      <h2 class="reports__heading">{{ t('reports.title') }}</h2>
      <NInput v-model:value="search" :placeholder="t('reports.search')" clearable size="small" />
      <NSelect
        class="reports__index-select"
        :value="selectedId"
        :options="indexOptions"
        :placeholder="t('reports.pick')"
        filterable
        @update:value="open"
      />
      <NAlert v-if="failure" type="error" :title="errorLabel(failure)" />
      <NSpin v-else-if="loadingIndex" size="small" />
      <NEmpty v-else-if="!index.length" :description="t('reports.none')" />
      <ul v-else class="reports__list">
        <li v-for="item in filtered" :key="item.id">
          <button
            type="button"
            :class="['reports__entry', { 'reports__entry--active': item.id === selectedId }]"
            @click="open(item.id)"
          >
            <span class="reports__entry-main">
              <strong>{{ item.ticker }}</strong>
              <RatingTag :rating="item.rating" />
            </span>
            <span class="reports__entry-meta">
              {{ item.tradeDate }}
              <template v-if="item.runs > 1">
                · {{ t('reports.runs', { count: item.runs }) }}</template
              >
            </span>
          </button>
        </li>
      </ul>
    </aside>

    <!-- Table of contents of the open report. -->
    <nav v-if="parts.length" class="reports__toc" :aria-label="t('reports.contents')">
      <h3 class="reports__heading">{{ t('reports.contents') }}</h3>
      <template v-for="(group, g) in toc" :key="g">
        <p v-if="group.title" class="reports__toc-group">{{ group.title }}</p>
        <ul class="reports__toc-list">
          <li v-for="item in group.items" :key="item.part.key">
            <a href="#" class="reports__toc-part" @click.prevent="jump(item.part.key)">{{
              item.title
            }}</a>
            <ul v-if="item.headings.length" class="reports__toc-list">
              <li v-for="heading in item.headings" :key="heading.index">
                <a
                  href="#"
                  :class="['reports__toc-heading', `reports__toc-heading--h${heading.level}`]"
                  @click.prevent="jump(item.part.key, heading.index)"
                  >{{ heading.text }}</a
                >
              </li>
            </ul>
          </li>
        </ul>
      </template>
    </nav>

    <!-- Reader. -->
    <main ref="reader" class="reports__reader">
      <NEmpty v-if="!selectedId && !loadingIndex" :description="t('reports.pick')" />
      <template v-else-if="selected">
        <header class="reports__title">
          <div>
            <h1>
              {{ selected.spec.ticker }}
              <span class="reports__date">{{ selected.spec.tradeDate }}</span>
            </h1>
            <div class="reports__meta">
              <RatingTag :rating="selected.rating" size="medium" />
              <NTag v-if="language" size="small" :bordered="false">
                {{ t('detail.reportLanguage', { language: languageLabel(language) }) }}
              </NTag>
              <RouterLink
                class="reports__link"
                :to="{ name: 'analysis', params: { id: selected.id } }"
              >
                {{ t('reports.openRun') }}
              </RouterLink>
            </div>
            <p v-if="entry && entry.runs > 1" class="reports__note">
              {{ t('reports.latestOnly', { count: entry.runs }) }}
            </p>
          </div>
          <NButtonGroup v-if="parts.length" size="small">
            <NButton @click="exportAs('md')">{{ t('reports.exportMd') }}</NButton>
            <NButton @click="exportAs('html')">{{ t('reports.exportHtml') }}</NButton>
            <NButton @click="exportAs('pdf')">{{ t('reports.exportPdf') }}</NButton>
          </NButtonGroup>
        </header>

        <NSpin v-if="loadingReport" />
        <NAlert v-else-if="reportFailure" type="warning" :title="errorLabel(reportFailure)" />
        <NEmpty v-else-if="!parts.length" :description="t('detail.noReports')" />

        <!-- On narrow screens the contents pane is hidden; it folds in here instead. -->
        <NCollapse v-if="parts.length" class="reports__toc-inline">
          <NCollapseItem :title="t('reports.contents')" name="toc">
            <ul class="reports__toc-list">
              <li v-for="part in parts" :key="part.key">
                <a href="#" class="reports__toc-part" @click.prevent="jump(part.key)">{{
                  partTitle(part)
                }}</a>
              </li>
            </ul>
          </NCollapseItem>
        </NCollapse>

        <template v-for="(part, i) in parts" :key="part.key">
          <h2 v-if="part.debate && parts[i - 1]?.debate !== part.debate" class="reports__group">
            {{ t(part.debate === 'investment' ? 'detail.investmentDebate' : 'detail.riskDebate') }}
          </h2>
          <section :data-part="part.key" class="reports__part">
            <h2 v-if="part.kind === 'section'" class="reports__part-title">
              {{ partTitle(part) }}
            </h2>
            <h3 v-else class="reports__part-title">{{ partTitle(part) }}</h3>
            <PriceChart v-if="part.key === 'market_report'" :analysis-id="selected.id" />
            <MarkdownView
              :source="part.markdown"
              :language="language"
              :charts="part.kind === 'section'"
            />
          </section>
        </template>
      </template>
    </main>
  </div>
</template>

<style scoped>
/* Three panes that scroll on their own; the page itself does not scroll on wide screens. */
.reports {
  display: grid;
  grid-template-columns: 240px 240px minmax(0, 1fr);
  gap: 24px;
  height: calc(100vh - 110px);
}

.reports__index,
.reports__toc,
.reports__reader {
  overflow-y: auto;
  min-height: 0;
}

.reports__index {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.reports__heading {
  margin: 0 0 4px;
  font-size: 1rem;
}

.reports__index-select,
.reports__toc-inline {
  display: none;
}

.reports__list,
.reports__toc-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.reports__toc-list .reports__toc-list {
  padding-left: 12px;
}

.reports__entry {
  display: flex;
  flex-direction: column;
  gap: 2px;
  width: 100%;
  padding: 8px 10px;
  border: 0;
  border-radius: 6px;
  background: none;
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.reports__entry:hover {
  background: rgba(128, 128, 128, 0.1);
}

.reports__entry--active {
  background: rgba(128, 128, 128, 0.18);
}

.reports__entry-main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.reports__entry-meta,
.reports__date,
.reports__note,
.reports__toc-group {
  opacity: 0.7;
  font-size: 0.85em;
}

.reports__toc-group {
  margin: 12px 0 4px;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.reports__toc a {
  display: block;
  padding: 3px 0;
  color: inherit;
  text-decoration: none;
}

.reports__toc a:hover {
  text-decoration: underline;
}

.reports__toc-part {
  font-weight: 600;
}

.reports__toc-heading {
  opacity: 0.8;
  font-size: 0.9em;
}

.reports__toc-heading--h3 {
  padding-left: 12px !important;
}

.reports__title {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.reports__title h1 {
  margin: 0 0 6px;
  font-size: 1.5rem;
}

.reports__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.reports__link {
  color: inherit;
  opacity: 0.8;
}

.reports__link:hover {
  opacity: 1;
}

.reports__note {
  margin: 6px 0 0;
}

.reports__group {
  margin: 32px 0 0;
  font-size: 1.25rem;
}

.reports__part {
  padding-top: 8px;
  scroll-margin-top: 8px;
}

.reports__part :deep(h1),
.reports__part :deep(h2),
.reports__part :deep(h3) {
  scroll-margin-top: 8px;
}

.reports__part-title {
  margin: 24px 0 8px;
  padding-bottom: 4px;
  border-bottom: 1px solid rgba(128, 128, 128, 0.3);
}

/* Medium: the contents pane folds into the reader. */
@media (max-width: 1100px) {
  .reports {
    grid-template-columns: 220px minmax(0, 1fr);
  }

  .reports__toc {
    display: none;
  }

  .reports__toc-inline {
    display: block;
    margin-bottom: 8px;
  }
}

/* Narrow: one column; the index becomes a picker and the page scrolls as a whole. */
@media (max-width: 760px) {
  .reports {
    display: block;
    height: auto;
  }

  .reports__index {
    margin-bottom: 16px;
  }

  .reports__list {
    display: none;
  }

  .reports__index-select {
    display: block;
  }
}
</style>
