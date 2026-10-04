<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { NButton, NCard, NEmpty, NSpin } from 'naive-ui'
import { analysisApi, CompareTable, type CompareColumn } from '@/features/analysis'
import { MarkdownView, reportsApi, SECTIONS } from '@/features/reports'
import { useLabels } from '@/shared/composables/useLabels'

const MAX_RUNS = 4

const route = useRoute()
const { t, error: errorLabel } = useLabels()
const columns = ref<CompareColumn[]>([])
const loading = ref(false)

const ids = computed(() => {
  const raw = [route.query.ids].flat().join(',')
  const unique = new Set(
    raw
      .split(',')
      .map((id) => id.trim())
      .filter(Boolean),
  )
  return [...unique].slice(0, MAX_RUNS)
})

async function loadColumn(id: string): Promise<CompareColumn> {
  try {
    const analysis = await analysisApi.getAnalysis(id)
    // A run that is not completed has no report to show; its column still lists spec and stats.
    const report =
      analysis.status === 'COMPLETED' ? await reportsApi.getReport(id).catch(() => null) : null
    return { id, run: { analysis, report }, error: null }
  } catch (e) {
    return { id, run: null, error: errorLabel(e) }
  }
}

async function load(list: string[]) {
  if (list.length < 2) {
    columns.value = []
    return
  }
  loading.value = true
  const result = await Promise.all(list.map(loadColumn))
  // Ignore an answer for ids the user has navigated away from.
  if (list.join(',') === ids.value.join(',')) columns.value = result
  loading.value = false
}

watch(ids, (list) => void load(list), { immediate: true })
</script>

<template>
  <NCard :title="t('compare.title')">
    <template #header-extra>
      <RouterLink :to="{ name: 'analyses' }" custom v-slot="{ navigate }">
        <NButton secondary @click="navigate">{{ t('compare.back') }}</NButton>
      </RouterLink>
    </template>
    <NEmpty v-if="ids.length < 2" :description="t('compare.empty')" />
    <NSpin v-else-if="loading && !columns.length" />
    <CompareTable v-else :columns="columns" :sections="SECTIONS">
      <template #markdown="{ source }">
        <MarkdownView :source="source" />
      </template>
    </CompareTable>
  </NCard>
</template>
