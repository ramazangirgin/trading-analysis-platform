<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { NAlert, NButton, NFormItem, NInputNumber, NSelect, NSpace, useMessage } from 'naive-ui'
import { ApiError } from '@/shared/api/error'
import type { ModelOption } from '@/shared/api/types'
import { useLabels } from '@/shared/composables/useLabels'
import { useCatalogStore } from '@/features/catalog'
import { settingsApi } from '../api'

const { t, error: errorLabel } = useLabels()
const message = useMessage()
const catalogStore = useCatalogStore()
const form = reactive({
  llmProvider: '',
  deepThinkLlm: '',
  quickThinkLlm: '',
  maxDebateRounds: 1,
  maxRiskDiscussRounds: 1,
})
const version = ref<number | null>(null)
const saving = ref(false)
const failure = ref<unknown>(null)

const catalog = computed(() => catalogStore.catalog)
const provider = computed(
  () => catalog.value?.providers.find((p) => p.id === form.llmProvider) ?? null,
)
const providerOptions = computed(
  () => catalog.value?.providers.map((p) => ({ label: p.id, value: p.id })) ?? [],
)
const toOptions = (models: ModelOption[]) =>
  models.map((m) => ({ label: `${m.id} — ${m.label}`, value: m.id }))
const deepOptions = computed(() => toOptions(provider.value?.deepModels ?? []))
const quickOptions = computed(() => toOptions(provider.value?.quickModels ?? []))

async function load() {
  try {
    await catalogStore.load()
    const defaults = await settingsApi.getAnalysisDefaults()
    Object.assign(form, {
      llmProvider: defaults.llmProvider,
      deepThinkLlm: defaults.deepThinkLlm,
      quickThinkLlm: defaults.quickThinkLlm,
      maxDebateRounds: defaults.maxDebateRounds,
      maxRiskDiscussRounds: defaults.maxRiskDiscussRounds,
    })
    version.value = defaults.version
    failure.value = null
  } catch (e) {
    failure.value = e
  }
}

// A different provider means different models; keep a model only if the new provider lists it.
watch(
  () => form.llmProvider,
  () => {
    if (!provider.value) return
    if (!deepOptions.value.some((o) => o.value === form.deepThinkLlm)) {
      form.deepThinkLlm = deepOptions.value[0]?.value ?? ''
    }
    if (!quickOptions.value.some((o) => o.value === form.quickThinkLlm)) {
      form.quickThinkLlm = quickOptions.value[0]?.value ?? ''
    }
  },
)

async function save() {
  saving.value = true
  try {
    const saved = await settingsApi.updateAnalysisDefaults({
      ...form,
      version: version.value ?? undefined,
    })
    version.value = saved.version
    failure.value = null
    message.success(t('settings.analysisDefaults.saved'))
  } catch (e) {
    message.error(errorLabel(e))
    if (e instanceof ApiError && e.errorCode === 'concurrent_update') await load()
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <NSpace vertical :size="12">
    <h3 class="heading">{{ t('settings.analysisDefaults.title') }}</h3>
    <p class="note">{{ t('settings.analysisDefaults.note') }}</p>
    <NAlert v-if="failure" type="error" :title="errorLabel(failure)" />
    <NAlert v-if="catalogStore.error" type="warning" :title="t('form.catalogUnavailable')" />
    <div class="grid">
      <NFormItem :label="t('settings.analysisDefaults.provider')">
        <NSelect v-model:value="form.llmProvider" :options="providerOptions" filterable />
      </NFormItem>
      <NFormItem :label="t('settings.analysisDefaults.deepModel')">
        <NSelect v-model:value="form.deepThinkLlm" :options="deepOptions" filterable />
      </NFormItem>
      <NFormItem :label="t('settings.analysisDefaults.quickModel')">
        <NSelect v-model:value="form.quickThinkLlm" :options="quickOptions" filterable />
      </NFormItem>
      <NFormItem :label="t('settings.analysisDefaults.debateRounds')">
        <NInputNumber v-model:value="form.maxDebateRounds" :min="1" :max="10" />
      </NFormItem>
      <NFormItem :label="t('settings.analysisDefaults.riskRounds')">
        <NInputNumber v-model:value="form.maxRiskDiscussRounds" :min="1" :max="10" />
      </NFormItem>
    </div>
    <NButton
      type="primary"
      :loading="saving"
      :disabled="!!failure || !!catalogStore.error"
      @click="save"
    >
      {{ t('settings.analysisDefaults.save') }}
    </NButton>
  </NSpace>
</template>

<style scoped>
.heading {
  margin: 0;
}

.note {
  margin: 0;
  opacity: 0.75;
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 0 16px;
}
</style>
