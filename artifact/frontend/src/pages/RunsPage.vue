<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { NAlert, NButton, NCard, NEmpty, NInput, NSelect, NSpace, useMessage } from 'naive-ui'
import { ACTIVE_STATUSES, analysisApi, AnalysisTable } from '@/features/analysis'
import type { Analysis, AnalysisStatus } from '@/shared/api/types'
import { useLabels } from '@/shared/composables/useLabels'
import { usePolling } from '@/shared/composables/usePolling'
import { reportsApi } from '@/features/reports'

const { t, error: errorLabel } = useLabels()
const analyses = ref<Analysis[]>([])
const loading = ref(true)
const failure = ref<unknown>(null)
const ticker = ref('')
const status = ref<AnalysisStatus | null>(null)
const scanning = ref(false)
const message = useMessage()

const statusOptions = computed(() =>
  (['QUEUED', 'RUNNING', 'COMPLETED', 'STOPPED', 'FAILED'] as const).map((value) => ({
    value,
    label: t(`status.${value}`),
  })),
)

async function refresh() {
  try {
    analyses.value = await analysisApi.listAnalyses({
      status: status.value ?? undefined,
      ticker: ticker.value.trim() || undefined,
    })
    failure.value = null
  } catch (e) {
    failure.value = e
  } finally {
    loading.value = false
  }
}

async function rescan() {
  scanning.value = true
  try {
    message.success(t('runs.rescanned', { ...(await reportsApi.rescanReports()) }))
    await refresh()
  } catch (e) {
    message.error(errorLabel(e))
  } finally {
    scanning.value = false
  }
}

watch([status, ticker], refresh)
usePolling(refresh, () => analyses.value.some((a) => ACTIVE_STATUSES.includes(a.status)))
</script>

<template>
  <NCard :title="t('runs.title')">
    <template #header-extra>
      <NSpace>
        <NButton secondary :loading="scanning" @click="rescan">{{ t('runs.rescan') }}</NButton>
        <RouterLink :to="{ name: 'new-analysis' }" custom v-slot="{ navigate }">
          <NButton type="primary" @click="navigate">{{ t('nav.newAnalysis') }}</NButton>
        </RouterLink>
      </NSpace>
    </template>
    <NSpace vertical :size="12">
      <div class="filters">
        <NInput v-model:value="ticker" :placeholder="t('runs.filterTicker')" clearable />
        <NSelect
          v-model:value="status"
          :options="statusOptions"
          :placeholder="t('runs.filterStatus')"
          clearable
        />
      </div>
      <NAlert v-if="failure" type="error" :title="errorLabel(failure)" />
      <AnalysisTable v-if="analyses.length || loading" :analyses="analyses" :loading="loading" />
      <NEmpty v-else :description="t('runs.empty')" />
    </NSpace>
  </NCard>
</template>

<style scoped>
.filters {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 260px));
  gap: 8px;
}
</style>
