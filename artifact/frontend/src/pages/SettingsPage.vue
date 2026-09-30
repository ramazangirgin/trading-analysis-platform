<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import {
  NAlert,
  NButton,
  NCard,
  NDataTable,
  NInput,
  NSpace,
  NTag,
  useMessage,
  type DataTableColumns,
} from 'naive-ui'
import { api, type SecretStatus } from '@/api/client'
import { useCatalogStore } from '@/stores/catalog'
import { useLabels } from '@/composables/useLabels'
import PresetManager from '@/components/PresetManager.vue'

interface KeyRow {
  env: string
  providers: string[]
  status: SecretStatus | null
}

const { t, error: errorLabel } = useLabels()
const message = useMessage()
const catalogStore = useCatalogStore()
const secrets = ref<SecretStatus[]>([])
const drafts = reactive<Record<string, string>>({})
const saving = ref<string | null>(null)
const failure = ref<unknown>(null)

// One row per key variable; several providers may share one (e.g. none today, but upstream allows it).
const rows = computed<KeyRow[]>(() => {
  const byEnv = new Map<string, string[]>()
  for (const provider of catalogStore.catalog?.providers ?? []) {
    if (!provider.apiKeyEnv) continue
    byEnv.set(provider.apiKeyEnv, [...(byEnv.get(provider.apiKeyEnv) ?? []), provider.id])
  }
  return [...byEnv.entries()]
    .map(([env, providers]) => ({
      env,
      providers,
      status: secrets.value.find((secret) => secret.name === env) ?? null,
    }))
    .sort((a, b) => Number(!!b.status) - Number(!!a.status) || a.env.localeCompare(b.env))
})

async function load() {
  try {
    await catalogStore.load()
    secrets.value = await api.listSecrets()
    failure.value = null
  } catch (e) {
    failure.value = e
  }
}

async function save(env: string) {
  saving.value = env
  try {
    await api.setSecret(env, drafts[env] ?? '')
    drafts[env] = ''
    message.success(t('settings.saved', { name: env }))
    secrets.value = await api.listSecrets()
  } catch (e) {
    message.error(errorLabel(e))
  } finally {
    saving.value = null
  }
}

async function remove(env: string) {
  saving.value = env
  try {
    await api.removeSecret(env)
    message.success(t('settings.removed', { name: env }))
    secrets.value = await api.listSecrets()
  } catch (e) {
    message.error(errorLabel(e))
  } finally {
    saving.value = null
  }
}

const columns = computed<DataTableColumns<KeyRow>>(() => [
  { title: t('settings.provider'), key: 'providers', render: (row) => row.providers.join(', ') },
  { title: t('settings.variable'), key: 'env', render: (row) => h('code', row.env) },
  {
    title: t('settings.status'),
    key: 'status',
    render: (row) =>
      row.status
        ? h(NSpace, { size: 4, align: 'center' }, () => [
            h(NTag, { type: 'success', size: 'small', bordered: false }, () =>
              row.status!.source === 'PLATFORM'
                ? t('settings.fromPlatform')
                : t('settings.fromFile'),
            ),
            h('code', row.status!.maskedValue),
          ])
        : h(NTag, { size: 'small', bordered: false }, () => t('settings.notSet')),
  },
  {
    title: t('settings.newValue'),
    key: 'edit',
    render: (row) =>
      h(NSpace, { size: 4, wrap: false }, () => [
        h(NInput, {
          type: 'password',
          showPasswordOn: 'click',
          size: 'small',
          value: drafts[row.env] ?? '',
          'onUpdate:value': (value: string) => (drafts[row.env] = value),
          inputProps: { autocomplete: 'off' },
          style: 'min-width: 180px',
        }),
        h(
          NButton,
          {
            size: 'small',
            type: 'primary',
            disabled: !drafts[row.env]?.trim(),
            loading: saving.value === row.env,
            onClick: () => save(row.env),
          },
          () => t('settings.save'),
        ),
        row.status?.source === 'PLATFORM'
          ? h(
              NButton,
              { size: 'small', secondary: true, type: 'error', onClick: () => remove(row.env) },
              () => t('settings.remove'),
            )
          : null,
      ]),
  },
])

onMounted(load)
</script>

<template>
  <NCard :title="t('settings.title')">
    <NSpace vertical :size="12">
      <h3 class="heading">{{ t('settings.keys') }}</h3>
      <p class="note">{{ t('settings.keysNote') }}</p>
      <NAlert v-if="failure" type="error" :title="errorLabel(failure)" />
      <NDataTable
        :columns="columns"
        :data="rows"
        :row-key="(row: KeyRow) => row.env"
        :bordered="false"
        size="small"
        :scroll-x="820"
      />
      <h3 class="heading">{{ t('presets.title') }}</h3>
      <p class="note">{{ t('presets.note') }}</p>
      <PresetManager />
    </NSpace>
  </NCard>
</template>

<style scoped>
.heading {
  margin: 0;
}

.note {
  margin: 0;
  opacity: 0.75;
}
</style>
