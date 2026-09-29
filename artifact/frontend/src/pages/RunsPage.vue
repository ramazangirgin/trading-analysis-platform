<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { NAlert, NButton, NCard, NEmpty, NInput, NSelect, NSpace } from 'naive-ui'
import { ACTIVE_STATUSES, api, type Analysis, type AnalysisStatus } from '@/api/client'
import AnalysisTable from '@/components/AnalysisTable.vue'
import { useLabels } from '@/composables/useLabels'
import { usePolling } from '@/composables/usePolling'

const { t, error: errorLabel } = useLabels()
const analyses = ref<Analysis[]>([])
const loading = ref(true)
const failure = ref<unknown>(null)
const ticker = ref('')
const status = ref<AnalysisStatus | null>(null)

const statusOptions = computed(() =>
  (['QUEUED', 'RUNNING', 'COMPLETED', 'STOPPED', 'FAILED'] as const).map((value) => ({
    value,
    label: t(`status.${value}`),
  })),
)

async function refresh() {
  try {
    analyses.value = await api.listAnalyses({
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

watch([status, ticker], refresh)
usePolling(refresh, () => analyses.value.some((a) => ACTIVE_STATUSES.includes(a.status)))
</script>

<template>
  <NCard :title="t('runs.title')">
    <template #header-extra>
      <RouterLink :to="{ name: 'new-analysis' }" custom v-slot="{ navigate }">
        <NButton type="primary" @click="navigate">{{ t('nav.newAnalysis') }}</NButton>
      </RouterLink>
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
