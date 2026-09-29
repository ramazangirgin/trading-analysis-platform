<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  NAlert,
  NButton,
  NCard,
  NCheckbox,
  NCheckboxGroup,
  NDatePicker,
  NForm,
  NFormItem,
  NInput,
  NInputNumber,
  NRadioButton,
  NRadioGroup,
  NSelect,
  NSpace,
  NSwitch,
  NPopover,
  useMessage,
  type FormInst,
  type FormRules,
} from 'naive-ui'
import { api, type Analyst, type ModelOption, type Preset } from '@/api/client'
import { useCatalogStore } from '@/stores/catalog'
import { useLabels } from '@/composables/useLabels'
import HealthAlerts from '@/components/HealthAlerts.vue'

const router = useRouter()
const { locale } = useI18n()
const { t, error: errorLabel } = useLabels()
const catalogStore = useCatalogStore()

const ANALYSTS: Analyst[] = ['MARKET', 'SOCIAL', 'NEWS', 'FUNDAMENTALS']
const LANGUAGES = ['Turkish', 'English', 'German', 'French', 'Spanish', 'Chinese', 'Japanese']

/** The latest weekday up to today: upstream rejects future dates, markets are closed at weekends. */
function lastWeekday(): number {
  const date = new Date()
  date.setHours(12, 0, 0, 0)
  while (date.getDay() === 0 || date.getDay() === 6) date.setDate(date.getDate() - 1)
  return date.getTime()
}

const form = reactive({
  ticker: '',
  tradeDate: lastWeekday() as number | null,
  assetType: 'STOCK' as 'STOCK' | 'CRYPTO',
  analysts: [...ANALYSTS] as Analyst[],
  llmProvider: null as string | null,
  deepThinkLlm: null as string | null,
  quickThinkLlm: null as string | null,
  maxDebateRounds: 1,
  maxRiskDiscussRounds: 1,
  outputLanguage: locale.value === 'tr' ? 'Turkish' : 'English',
  checkpointEnabled: false,
})

// Presets keep the model and analyst choices; ticker and date are picked per run.
const PRESET_FIELDS = [
  'assetType',
  'analysts',
  'llmProvider',
  'deepThinkLlm',
  'quickThinkLlm',
  'maxDebateRounds',
  'maxRiskDiscussRounds',
  'outputLanguage',
  'checkpointEnabled',
] as const
const message = useMessage()
const presets = ref<Preset[]>([])
const presetName = ref('')
const presetOptions = computed(() => presets.value.map((p) => ({ label: p.name, value: p.id })))

async function loadPresets() {
  presets.value = await api.listPresets().catch(() => [])
}

function applyPreset(id: string | null) {
  const preset = presets.value.find((p) => p.id === id)
  if (!preset) return
  for (const field of PRESET_FIELDS) {
    if (field in preset.values) Object.assign(form, { [field]: preset.values[field] })
  }
}

async function savePreset() {
  const name = presetName.value.trim()
  if (!name) return
  const values = Object.fromEntries(PRESET_FIELDS.map((field) => [field, form[field]]))
  try {
    await api.createPreset(name, values)
    message.success(t('presets.saved', { name }))
    presetName.value = ''
    await loadPresets()
  } catch (e) {
    message.error(errorLabel(e))
  }
}

const formRef = ref<FormInst | null>(null)
const submitting = ref(false)
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

onMounted(async () => {
  void loadPresets()
  await catalogStore.load()
  const defaults = catalog.value?.defaults
  if (defaults && !form.llmProvider) {
    form.llmProvider = defaults.llmProvider
    form.deepThinkLlm = defaults.deepThinkLlm
    form.quickThinkLlm = defaults.quickThinkLlm
  }
})

// A different provider means different models; keep a model only if the new provider lists it.
watch(
  () => form.llmProvider,
  (next, previous) => {
    if (!previous || !provider.value) return
    const keep = (id: string | null, models: ModelOption[]) =>
      id && models.some((m) => m.id === id) ? id : (models[0]?.id ?? null)
    form.deepThinkLlm = keep(form.deepThinkLlm, provider.value.deepModels)
    form.quickThinkLlm = keep(form.quickThinkLlm, provider.value.quickModels)
  },
)

// Crypto pairs are detected as they are typed, like the upstream CLI does.
watch(
  () => form.ticker,
  (ticker) => {
    if (/-(USD|USDT|EUR|TRY)$/i.test(ticker.trim())) form.assetType = 'CRYPTO'
  },
)

const rules = computed<FormRules>(() => ({
  ticker: { required: true, trigger: ['blur', 'input'], message: t('form.ticker') },
  tradeDate: { required: true, type: 'number', trigger: 'change', message: t('form.tradeDate') },
  analysts: {
    required: true,
    type: 'array',
    min: 1,
    trigger: 'change',
    message: t('form.analysts'),
  },
  llmProvider: { required: true, trigger: 'change', message: t('form.provider') },
  deepThinkLlm: { required: true, trigger: ['blur', 'change'], message: t('form.deepModel') },
  quickThinkLlm: { required: true, trigger: ['blur', 'change'], message: t('form.quickModel') },
}))

const isoDate = (millis: number) => {
  const date = new Date(millis)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

async function submit() {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  submitting.value = true
  failure.value = null
  try {
    const analysis = await api.startAnalysis({
      ticker: form.ticker.trim().toUpperCase(),
      tradeDate: isoDate(form.tradeDate!),
      assetType: form.assetType,
      analysts: form.analysts,
      llmProvider: form.llmProvider!,
      deepThinkLlm: form.deepThinkLlm!,
      quickThinkLlm: form.quickThinkLlm!,
      maxDebateRounds: form.maxDebateRounds,
      maxRiskDiscussRounds: form.maxRiskDiscussRounds,
      outputLanguage: form.outputLanguage,
      checkpointEnabled: form.checkpointEnabled,
    })
    await router.push({ name: 'analysis', params: { id: analysis.id } })
  } catch (e) {
    failure.value = e
  } finally {
    submitting.value = false
  }
}

// Any time today is allowed: the picker hands over the start of the day, the default is noon.
const futureDate = (millis: number) => {
  const endOfToday = new Date()
  endOfToday.setHours(23, 59, 59, 999)
  return millis > endOfToday.getTime()
}
</script>

<template>
  <NCard :title="t('form.title')" class="new-analysis">
    <NSpace vertical :size="12">
      <HealthAlerts />
      <NAlert v-if="catalogStore.error" type="warning" :title="t('form.catalogUnavailable')" />
      <div class="presets">
        <NSelect
          :options="presetOptions"
          :placeholder="presets.length ? t('presets.load') : t('presets.none')"
          :disabled="!presets.length"
          clearable
          @update:value="applyPreset"
        />
        <NPopover trigger="click" placement="bottom-end">
          <template #trigger>
            <NButton secondary>{{ t('presets.save') }}</NButton>
          </template>
          <NSpace>
            <NInput
              v-model:value="presetName"
              :placeholder="t('presets.name')"
              @keyup.enter="savePreset"
            />
            <NButton type="primary" :disabled="!presetName.trim()" @click="savePreset">
              {{ t('settings.save') }}
            </NButton>
          </NSpace>
        </NPopover>
      </div>
      <NForm ref="formRef" :model="form" :rules="rules" label-placement="top">
        <div class="grid">
          <NFormItem :label="t('form.ticker')" path="ticker">
            <NInput v-model:value="form.ticker" :placeholder="t('form.tickerPlaceholder')" />
          </NFormItem>
          <NFormItem :label="t('form.tradeDate')" path="tradeDate">
            <NDatePicker
              v-model:value="form.tradeDate"
              type="date"
              :is-date-disabled="futureDate"
              style="width: 100%"
            />
          </NFormItem>
          <NFormItem :label="t('form.assetType')">
            <NRadioGroup v-model:value="form.assetType">
              <NRadioButton value="STOCK">{{ t('form.stock') }}</NRadioButton>
              <NRadioButton value="CRYPTO">{{ t('form.crypto') }}</NRadioButton>
            </NRadioGroup>
          </NFormItem>
        </div>

        <NFormItem :label="t('form.analysts')" path="analysts">
          <NCheckboxGroup v-model:value="form.analysts">
            <NSpace>
              <NCheckbox v-for="analyst in ANALYSTS" :key="analyst" :value="analyst">
                {{ t(`analysts.${analyst}`) }}
              </NCheckbox>
            </NSpace>
          </NCheckboxGroup>
        </NFormItem>

        <div class="grid">
          <NFormItem :label="t('form.provider')" path="llmProvider">
            <NSelect
              v-model:value="form.llmProvider"
              :options="providerOptions"
              :loading="!catalog && !catalogStore.error"
              filterable
            />
          </NFormItem>
          <NFormItem :label="t('form.deepModel')" path="deepThinkLlm">
            <NSelect
              v-model:value="form.deepThinkLlm"
              :options="deepOptions"
              filterable
              tag
              :placeholder="t('form.customModelHint')"
            />
          </NFormItem>
          <NFormItem :label="t('form.quickModel')" path="quickThinkLlm">
            <NSelect
              v-model:value="form.quickThinkLlm"
              :options="quickOptions"
              filterable
              tag
              :placeholder="t('form.customModelHint')"
            />
          </NFormItem>
        </div>
        <p v-if="provider?.apiKeyEnv" class="hint">
          {{ t('form.noKeyHint', { env: provider.apiKeyEnv }) }}
        </p>

        <div class="grid">
          <NFormItem :label="t('form.debateRounds')">
            <NInputNumber v-model:value="form.maxDebateRounds" :min="1" :max="5" />
          </NFormItem>
          <NFormItem :label="t('form.riskRounds')">
            <NInputNumber v-model:value="form.maxRiskDiscussRounds" :min="1" :max="5" />
          </NFormItem>
          <NFormItem :label="t('form.outputLanguage')">
            <NSelect
              v-model:value="form.outputLanguage"
              :options="LANGUAGES.map((value) => ({ value, label: value }))"
              filterable
              tag
            />
          </NFormItem>
        </div>
        <p class="hint">{{ t('form.outputLanguageNote') }}</p>

        <NFormItem :label="t('form.checkpoint')">
          <NSwitch v-model:value="form.checkpointEnabled" />
        </NFormItem>
      </NForm>

      <NAlert v-if="failure" type="error" :title="errorLabel(failure)" />
      <NButton type="primary" size="large" :loading="submitting" @click="submit">
        {{ t('form.submit') }}
      </NButton>
    </NSpace>
  </NCard>
</template>

<style scoped>
.new-analysis {
  max-width: 900px;
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  column-gap: 16px;
}

.presets {
  display: grid;
  grid-template-columns: minmax(0, 320px) auto;
  gap: 8px;
}

.hint {
  margin: -8px 0 16px;
  font-size: 12px;
  opacity: 0.7;
}
</style>
