<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import DisclaimerNotice from '@/shared/ui/DisclaimerNotice.vue'

const { t } = useI18n()
</script>

<template>
  <section>
    <h1>{{ t('about.title') }}</h1>
    <DisclaimerNotice />
  </section>
</template>
