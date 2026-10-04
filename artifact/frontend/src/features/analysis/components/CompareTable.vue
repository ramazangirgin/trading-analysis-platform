<script setup lang="ts">
import { computed, ref } from 'vue'
import { NAlert, NCollapse, NCollapseItem } from 'naive-ui'
import { useLabels } from '@/shared/composables/useLabels'
import RatingTag from '@/shared/ui/RatingTag.vue'
import {
  compareView,
  type CompareColumn,
  type CompareRow,
  type CompareRun,
} from '../model/compareView'

/**
 * The runs side by side, one column each. Markdown text (decision, report sections) is rendered by
 * the `markdown` slot, so this feature does not import the reports feature.
 */
const props = defineProps<{ columns: CompareColumn[]; sections: readonly string[] }>()
const { t, integer, usd, duration } = useLabels()

const loaded = computed(() => props.columns.filter((c) => c.run).map((c) => c.run as CompareRun))
const rows = computed(() => compareView(loaded.value, props.sections))
const tableRows = computed(() => rows.value.filter((r) => r.group !== 'section'))
const sectionRows = computed(() => rows.value.filter((r) => r.group === 'section'))
const expanded = ref<string[]>(['sections.final_trade_decision'])

/** The row's value in a column; failed columns have none. */
function value(row: CompareRow, column: CompareColumn) {
  const index = loaded.value.findIndex((run) => run === column.run)
  return index < 0 ? null : row.values[index]!
}

function text(row: CompareRow, column: CompareColumn): string {
  const v = value(row, column)
  if (v === null || v === '') return '—'
  if (row.format === 'integer') return integer(v as number)
  if (row.format === 'usd') return usd(v as number)
  if (row.format === 'duration') return duration(v as number)
  return String(v)
}

const model = (column: CompareColumn) => {
  const spec = column.run?.analysis.spec
  return spec ? `${spec.llmProvider} / ${spec.deepThinkLlm}` : ''
}
</script>

<template>
  <div class="compare">
    <p class="compare__legend"><span class="compare__swatch" />{{ t('compare.differs') }}</p>
    <div class="compare__scroll">
      <table class="compare__table">
        <thead>
          <tr>
            <th class="compare__label" />
            <th v-for="column in columns" :key="column.id" class="compare__head">
              <template v-if="column.run">
                <RouterLink :to="{ name: 'analysis', params: { id: column.id } }">
                  <strong>{{ column.run.analysis.spec.ticker }}</strong>
                  {{ column.run.analysis.spec.tradeDate }}
                </RouterLink>
                <div class="compare__sub">{{ model(column) }}</div>
              </template>
              <template v-else>
                <RouterLink :to="{ name: 'analysis', params: { id: column.id } }">{{
                  column.id
                }}</RouterLink>
                <NAlert type="error" :show-icon="false" class="compare__error">{{
                  column.error ?? t('compare.loadError')
                }}</NAlert>
              </template>
            </th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in tableRows" :key="row.labelKey" :data-row="row.labelKey">
            <th class="compare__label" scope="row">{{ t(row.labelKey) }}</th>
            <td
              v-for="column in columns"
              :key="column.id"
              :class="{ compare__differs: row.differs }"
            >
              <RatingTag
                v-if="row.format === 'rating' && value(row, column)"
                :rating="String(value(row, column))"
              />
              <slot
                v-else-if="row.format === 'markdown' && value(row, column)"
                name="markdown"
                :source="String(value(row, column))"
              />
              <template v-else>{{ text(row, column) }}</template>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <template v-if="sectionRows.length">
      <h2 class="compare__sections">{{ t('compare.reportSections') }}</h2>
      <NCollapse v-model:expanded-names="expanded">
        <NCollapseItem
          v-for="row in sectionRows"
          :key="row.labelKey"
          :name="row.labelKey"
          :title="t(row.labelKey)"
        >
          <div class="compare__grid" :style="{ '--columns': columns.length }">
            <div
              v-for="column in columns"
              :key="column.id"
              :class="['compare__cell', { compare__differs: row.differs }]"
            >
              <slot
                v-if="value(row, column)"
                name="markdown"
                :source="String(value(row, column))"
              />
              <span v-else class="compare__sub">{{ t('compare.noReport') }}</span>
            </div>
          </div>
        </NCollapseItem>
      </NCollapse>
    </template>
  </div>
</template>

<style scoped>
.compare__legend {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0 0 8px;
  font-size: 0.85em;
  opacity: 0.8;
}

.compare__swatch {
  width: 12px;
  height: 12px;
  border-radius: 2px;
  background: rgba(240, 160, 32, 0.25);
}

.compare__scroll {
  overflow-x: auto;
}

.compare__table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
}

.compare__table th,
.compare__table td {
  min-width: 220px;
  padding: 6px 10px;
  border-bottom: 1px solid rgba(128, 128, 128, 0.25);
  text-align: left;
  vertical-align: top;
  overflow-wrap: anywhere;
}

/* The row labels stay in view while the run columns scroll. */
.compare__table th.compare__label {
  position: sticky;
  left: 0;
  z-index: 1;
  min-width: 160px;
  width: 160px;
  background: var(--n-color, var(--n-merged-color, inherit));
  font-weight: 600;
}

.compare__head a {
  color: inherit;
}

.compare__sub {
  font-size: 0.85em;
  font-weight: normal;
  opacity: 0.7;
}

.compare__error {
  margin-top: 4px;
}

.compare__differs {
  background: rgba(240, 160, 32, 0.15);
}

.compare__sections {
  margin: 24px 0 8px;
  font-size: 1.25rem;
}

.compare__grid {
  display: grid;
  grid-template-columns: repeat(var(--columns), minmax(280px, 1fr));
  gap: 12px;
  overflow-x: auto;
}

.compare__cell {
  min-width: 0;
  padding: 4px 10px;
}
</style>
