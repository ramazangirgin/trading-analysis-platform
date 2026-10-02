<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { NSelect } from 'naive-ui'
import { type AppLocale, DEFAULT_LOCALE, isAppLocale, setLocale } from '@/shared/i18n'

const { t, locale } = useI18n()

const options = [
  { label: 'English', value: 'en' },
  { label: 'Türkçe', value: 'tr' },
]

const selected = computed<AppLocale>({
  get: () => (isAppLocale(locale.value) ? locale.value : DEFAULT_LOCALE),
  set: setLocale,
})
</script>

<template>
  <NSelect
    v-model:value="selected"
    :options="options"
    :aria-label="t('app.language')"
    size="small"
    style="width: 120px"
  />
</template>
