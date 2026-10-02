<script setup lang="ts">
import { NTag } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import type { AnalysisStatus } from '@/shared/api/types'

defineProps<{ status: AnalysisStatus }>()
const { t } = useI18n()

const types = {
  QUEUED: 'default',
  RUNNING: 'info',
  COMPLETED: 'success',
  STOPPED: 'warning',
  FAILED: 'error',
} as const
</script>

<template>
  <NTag :type="types[status]" size="small" round :bordered="false">{{
    t(`status.${status}`)
  }}</NTag>
</template>
