<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { NSelect } from 'naive-ui'
import { isAppLocale, storeLocale, type AppLocale } from '@/i18n'

const { t, locale } = useI18n()

const options = [
  { label: 'Türkçe', value: 'tr' },
  { label: 'English', value: 'en' },
]

const selected = computed<AppLocale>({
  get: () => (isAppLocale(locale.value) ? locale.value : 'tr'),
  set: (value) => {
    locale.value = value
    storeLocale(value)
  },
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
