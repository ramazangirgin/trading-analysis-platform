<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { AppLocale } from '@/shared/i18n'

defineProps<{ locale?: AppLocale; compact?: boolean }>()
const { t } = useI18n()
</script>

<template>
  <p role="note" class="disclaimer" :class="{ compact }">
    {{ locale ? t('app.disclaimer', {}, { locale }) : t('app.disclaimer') }}
  </p>
</template>

<style scoped>
.disclaimer {
  margin: 0;
  color: var(--n-text-color-3, #767c82);
}

.compact {
  font-size: 12px;
  line-height: 1.4;
  overflow-wrap: anywhere;
}
</style>
