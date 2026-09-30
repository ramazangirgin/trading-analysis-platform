<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import {
  NAlert,
  NButton,
  NDataTable,
  NEmpty,
  NInput,
  NPopconfirm,
  NSpace,
  useMessage,
  type DataTableColumns,
} from 'naive-ui'
import { api, type Preset } from '@/api/client'
import { useLabels } from '@/composables/useLabels'

/** Saved New Analysis presets: what each holds, rename, delete. Presets are made on New Analysis. */
const { t, error: errorLabel, language, dateTime } = useLabels()
const message = useMessage()
const presets = ref<Preset[]>([])
const names = reactive<Record<string, string>>({})
const busy = ref<string | null>(null)
const failure = ref<unknown>(null)

async function load() {
  try {
    presets.value = await api.listPresets()
    for (const preset of presets.value) names[preset.id] = preset.name
    failure.value = null
  } catch (e) {
    failure.value = e
  }
}

async function rename(preset: Preset) {
  const name = names[preset.id]?.trim()
  if (!name || name === preset.name) return
  busy.value = preset.id
  try {
    await api.updatePreset(preset.id, name, preset.values)
    message.success(t('presets.renamed', { name }))
    await load()
  } catch (e) {
    message.error(errorLabel(e))
  } finally {
    busy.value = null
  }
}

async function remove(preset: Preset) {
  busy.value = preset.id
  try {
    await api.deletePreset(preset.id)
    message.success(t('presets.deleted', { name: preset.name }))
    await load()
  } catch (e) {
    message.error(errorLabel(e))
  } finally {
    busy.value = null
  }
}

/** "deepseek · deepseek-v4-pro / deepseek-v4-flash · 4 analysts · 2 rounds · Turkish" */
function summary(values: Record<string, unknown>): string {
  const parts: string[] = []
  if (values.llmProvider) parts.push(String(values.llmProvider))
  if (values.deepThinkLlm || values.quickThinkLlm) {
    parts.push(`${values.deepThinkLlm ?? '—'} / ${values.quickThinkLlm ?? '—'}`)
  }
  if (Array.isArray(values.analysts)) {
    parts.push(
      values.analysts.map((analyst) => t(`analysts.${String(analyst).toUpperCase()}`)).join(', '),
    )
  }
  if (typeof values.maxDebateRounds === 'number') {
    parts.push(
      t('presets.rounds', {
        debate: values.maxDebateRounds,
        risk: values.maxRiskDiscussRounds ?? '—',
      }),
    )
  }
  if (typeof values.outputLanguage === 'string') parts.push(language(values.outputLanguage))
  return parts.join(' · ')
}

const columns = computed<DataTableColumns<Preset>>(() => [
  {
    title: t('presets.name'),
    key: 'name',
    width: 280,
    render: (preset) =>
      h(NSpace, { size: 4, wrap: false }, () => [
        h(NInput, {
          size: 'small',
          value: names[preset.id] ?? preset.name,
          maxlength: 80,
          'onUpdate:value': (value: string) => (names[preset.id] = value),
          onKeyup: (event: KeyboardEvent) => event.key === 'Enter' && rename(preset),
        }),
        h(
          NButton,
          {
            size: 'small',
            disabled: !names[preset.id]?.trim() || names[preset.id]?.trim() === preset.name,
            loading: busy.value === preset.id,
            onClick: () => rename(preset),
          },
          () => t('presets.rename'),
        ),
      ]),
  },
  { title: t('presets.values'), key: 'values', render: (preset) => summary(preset.values) },
  {
    title: t('presets.updated'),
    key: 'updatedAt',
    width: 150,
    render: (preset) => dateTime(preset.updatedAt),
  },
  {
    title: '',
    key: 'delete',
    width: 90,
    render: (preset) =>
      h(
        NPopconfirm,
        { onPositiveClick: () => remove(preset) },
        {
          trigger: () =>
            h(NButton, { size: 'small', secondary: true, type: 'error' }, () =>
              t('presets.delete'),
            ),
          default: () => t('presets.confirmDelete', { name: preset.name }),
        },
      ),
  },
])

onMounted(load)
</script>

<template>
  <NSpace vertical :size="12">
    <NAlert v-if="failure" type="error" :title="errorLabel(failure)" />
    <NEmpty v-else-if="!presets.length" :description="t('presets.emptyHint')" />
    <NDataTable
      v-else
      :columns="columns"
      :data="presets"
      :row-key="(preset: Preset) => preset.id"
      :bordered="false"
      size="small"
      :scroll-x="820"
    />
  </NSpace>
</template>
