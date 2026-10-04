<script setup lang="ts">
import { computed, h } from 'vue'
import { useRouter } from 'vue-router'
import { NDataTable, type DataTableColumns } from 'naive-ui'
import type { Analysis } from '@/shared/api/types'
import { useLabels } from '@/shared/composables/useLabels'
import StatusTag from '@/shared/ui/StatusTag.vue'
import RatingTag from '@/shared/ui/RatingTag.vue'

const props = defineProps<{
  analyses: Analysis[]
  loading?: boolean
  compact?: boolean
  /** Adds a checkbox column; the ticked run ids are the `v-model:checked`. */
  selectable?: boolean
}>()
const checked = defineModel<string[]>('checked', { default: () => [] })
const router = useRouter()
const { t, startTime, duration } = useLabels()

const columns = computed<DataTableColumns<Analysis>>(() => {
  const all: DataTableColumns<Analysis> = [
    ...(props.selectable ? [{ type: 'selection' as const }] : []),
    { title: t('runs.ticker'), key: 'ticker', render: (row) => h('strong', row.spec.ticker) },
    {
      title: t('runs.tradeDate'),
      key: 'tradeDate',
      render: (row) =>
        h('div', [
          h('div', row.spec.tradeDate),
          h('div', { class: 'started' }, t('runs.startedAt', { time: startTime(row) })),
        ]),
    },
    {
      title: t('runs.status'),
      key: 'status',
      render: (row) => h(StatusTag, { status: row.status }),
    },
    {
      title: t('runs.rating'),
      key: 'rating',
      render: (row) => h(RatingTag, { rating: row.rating }),
    },
  ]
  if (!props.compact) {
    all.push(
      {
        title: t('runs.model'),
        key: 'model',
        render: (row) =>
          row.source === 'EXTERNAL'
            ? t('runs.imported')
            : `${row.spec.llmProvider} / ${row.spec.deepThinkLlm}`,
      },
      {
        title: t('runs.duration'),
        key: 'duration',
        render: (row) => duration(row.stats.elapsedMs),
      },
    )
  }
  return all
})

const rowProps = (row: Analysis) => ({
  style: 'cursor: pointer',
  onClick: (event: MouseEvent) => {
    // Ticking a checkbox must not open the run.
    if ((event.target as HTMLElement).closest('.n-data-table-td--selection')) return
    void router.push({ name: 'analysis', params: { id: row.id } })
  },
})
</script>

<template>
  <NDataTable
    :columns="columns"
    :data="analyses"
    :loading="loading"
    :row-key="(row: Analysis) => row.id"
    :row-props="rowProps"
    :checked-row-keys="checked"
    @update:checked-row-keys="(keys) => (checked = keys as string[])"
    :bordered="false"
    size="small"
    :scroll-x="compact ? undefined : 760"
  />
</template>

<style scoped>
:deep(.started) {
  font-size: 12px;
  opacity: 0.6;
  white-space: nowrap;
}
</style>
