<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { NAlert, NSpace } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import type { HealthCheck } from '@/shared/api/types'
import { healthApi } from '../api'

/** Shows only what needs attention: a DOWN runner, missing keys. Silent when all is well. */
const { t } = useI18n()
const problems = ref<HealthCheck[]>([])

onMounted(async () => {
  try {
    const health = await healthApi.getHealth()
    problems.value = health.checks.filter(
      (check) => check.status !== 'UP' && check.code !== 'data_dir_empty',
    )
  } catch {
    problems.value = []
  }
})
</script>

<template>
  <NSpace v-if="problems.length" vertical>
    <NAlert
      v-for="check in problems"
      :key="check.name"
      :type="check.status === 'DOWN' ? 'error' : 'warning'"
      :title="t(`health.${check.code}`, check.params)"
    >
      <RouterLink v-if="check.code === 'no_provider_keys'" :to="{ name: 'settings' }">
        {{ t('nav.settings') }}
      </RouterLink>
    </NAlert>
  </NSpace>
</template>
