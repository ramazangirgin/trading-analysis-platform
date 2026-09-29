<script setup lang="ts">
import { computed, h } from 'vue'
import { useRouter } from 'vue-router'
import { NDataTable, type DataTableColumns } from 'naive-ui'
import type { Analysis } from '@/api/client'
import { useLabels } from '@/composables/useLabels'
import StatusTag from './StatusTag.vue'
import RatingTag from './RatingTag.vue'

const props = defineProps<{ analyses: Analysis[]; loading?: boolean; compact?: boolean }>()
const router = useRouter()
const { t, dateTime, duration } = useLabels()

const columns = computed<DataTableColumns<Analysis>>(() => {
  const all: DataTableColumns<Analysis> = [
    { title: t('runs.ticker'), key: 'ticker', render: (row) => h('strong', row.spec.ticker) },
    { title: t('runs.tradeDate'), key: 'tradeDate', render: (row) => row.spec.tradeDate },
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
        render: (row) => `${row.spec.llmProvider} / ${row.spec.deepThinkLlm}`,
      },
      { title: t('runs.created'), key: 'created', render: (row) => dateTime(row.createdAt) },
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
  onClick: () => router.push({ name: 'analysis', params: { id: row.id } }),
})
</script>

<template>
  <NDataTable
    :columns="columns"
    :data="analyses"
    :loading="loading"
    :row-key="(row: Analysis) => row.id"
    :row-props="rowProps"
    :bordered="false"
    size="small"
    :scroll-x="compact ? undefined : 760"
  />
</template>
