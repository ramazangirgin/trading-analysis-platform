<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { NCard, NSpace, NTag, NText } from 'naive-ui'
import { fetchBackendStatus, type BackendStatus } from '@/api/health'

const { t } = useI18n()
const status = ref<BackendStatus | 'checking'>('checking')
const controller = new AbortController()

onMounted(async () => {
  status.value = await fetchBackendStatus(controller.signal)
})
onBeforeUnmount(() => controller.abort())

const tagType = { checking: 'default', up: 'success', down: 'error' } as const
</script>

<template>
  <NCard :title="t('home.heading')">
    <NSpace vertical>
      <NText>{{ t('home.intro') }}</NText>
      <NSpace align="center">
        <NText depth="3">{{ t('home.backend') }}</NText>
        <NTag :type="tagType[status]" size="small" round>{{ t(`home.status.${status}`) }}</NTag>
      </NSpace>
    </NSpace>
  </NCard>
</template>
