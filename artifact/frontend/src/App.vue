<script setup lang="ts">
import { computed, watchEffect } from 'vue'
import { useI18n } from 'vue-i18n'
import { usePreferredDark } from '@vueuse/core'
import {
  NConfigProvider,
  NLayout,
  NLayoutContent,
  NLayoutHeader,
  NMessageProvider,
  darkTheme,
  dateEnUS,
  dateTrTR,
  enUS,
  trTR,
} from 'naive-ui'
import LocaleSwitcher from '@/components/LocaleSwitcher.vue'

const { t, locale } = useI18n()
const preferredDark = usePreferredDark()

const theme = computed(() => (preferredDark.value ? darkTheme : null))
const naiveLocale = computed(() => (locale.value === 'tr' ? trTR : enUS))
const naiveDateLocale = computed(() => (locale.value === 'tr' ? dateTrTR : dateEnUS))

watchEffect(() => {
  document.documentElement.lang = locale.value
  document.title = t('app.title')
})
</script>

<template>
  <NConfigProvider :theme="theme" :locale="naiveLocale" :date-locale="naiveDateLocale">
    <NMessageProvider>
      <NLayout class="app">
        <NLayoutHeader bordered class="app__header">
          <RouterLink to="/" class="app__brand">{{ t('app.title') }}</RouterLink>
          <nav class="app__nav">
            <RouterLink :to="{ name: 'dashboard' }">{{ t('nav.dashboard') }}</RouterLink>
            <RouterLink :to="{ name: 'analyses' }">{{ t('nav.analyses') }}</RouterLink>
            <RouterLink :to="{ name: 'new-analysis' }">{{ t('nav.newAnalysis') }}</RouterLink>
          </nav>
          <LocaleSwitcher />
        </NLayoutHeader>
        <NLayoutContent class="app__content">
          <RouterView />
        </NLayoutContent>
      </NLayout>
    </NMessageProvider>
  </NConfigProvider>
</template>

<style>
body {
  margin: 0;
}

.app {
  min-height: 100vh;
}

.app__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 16px;
}

.app__brand {
  font-weight: 600;
  color: inherit;
  text-decoration: none;
}

.app__nav {
  display: flex;
  gap: 16px;
  flex: 1;
  flex-wrap: wrap;
}

.app__nav a {
  color: inherit;
  text-decoration: none;
  opacity: 0.75;
}

.app__nav a.router-link-exact-active {
  opacity: 1;
  font-weight: 600;
}

.app__content {
  padding: 24px 16px;
  max-width: 1100px;
  margin: 0 auto;
}
</style>
