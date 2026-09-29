<script setup lang="ts">
import { computed } from 'vue'
import { NTag } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import type { Rating } from '@/api/client'

/** Accepts the enum ("OVERWEIGHT") or upstream's spelling ("Overweight"). */
const props = defineProps<{ rating: string | null; size?: 'small' | 'medium' | 'large' }>()
const { t } = useI18n()

const types: Record<Rating, 'success' | 'warning' | 'error' | 'info' | 'default'> = {
  BUY: 'success',
  OVERWEIGHT: 'success',
  HOLD: 'warning',
  UNDERWEIGHT: 'error',
  SELL: 'error',
  REVIEW: 'info',
}

const key = computed<Rating | null>(() => {
  const normalized = props.rating?.toUpperCase().replace(/[^A-Z]/g, '')
  return normalized && normalized in types ? (normalized as Rating) : null
})
</script>

<template>
  <NTag v-if="key" :type="types[key]" :size="size ?? 'small'" :bordered="false">
    {{ t(`rating.${key}`) }}
  </NTag>
  <span v-else>—</span>
</template>
