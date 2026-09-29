<script setup lang="ts">
import { computed, ref } from 'vue'
import { NAlert, NButton, NCard, NEmpty, NSpace } from 'naive-ui'
import { ACTIVE_STATUSES, api, type Analysis } from '@/api/client'
import AnalysisTable from '@/components/AnalysisTable.vue'
import HealthAlerts from '@/components/HealthAlerts.vue'
import { useLabels } from '@/composables/useLabels'
import { usePolling } from '@/composables/usePolling'

const { t, error: errorLabel } = useLabels()
const analyses = ref<Analysis[]>([])
const loading = ref(true)
const failure = ref<unknown>(null)

const active = computed(() => analyses.value.filter((a) => ACTIVE_STATUSES.includes(a.status)))
const recent = computed(() => analyses.value.filter((a) => a.status === 'COMPLETED').slice(0, 8))

async function refresh() {
  try {
    analyses.value = await api.listAnalyses()
    failure.value = null
  } catch (e) {
    failure.value = e
  } finally {
    loading.value = false
  }
}

usePolling(refresh, () => active.value.length > 0 || failure.value !== null)
</script>

<template>
  <NSpace vertical :size="16">
    <HealthAlerts />
    <NAlert v-if="failure" type="error" :title="errorLabel(failure)" />
    <NCard :title="t('dashboard.active')">
      <template #header-extra>
        <RouterLink :to="{ name: 'new-analysis' }" custom v-slot="{ navigate }">
          <NButton type="primary" @click="navigate">{{ t('dashboard.start') }}</NButton>
        </RouterLink>
      </template>
      <AnalysisTable v-if="active.length" :analyses="active" compact />
      <NEmpty v-else-if="!loading" :description="t('dashboard.noActive')" />
    </NCard>
    <NCard :title="t('dashboard.recent')">
      <AnalysisTable v-if="recent.length" :analyses="recent" compact />
      <NEmpty v-else-if="!loading" :description="t('dashboard.noRecent')" />
    </NCard>
  </NSpace>
</template>
