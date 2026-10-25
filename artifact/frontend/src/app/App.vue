<script setup lang="ts">
import { computed, watchEffect } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { usePreferredDark } from '@vueuse/core'
import {
  NConfigProvider,
  NDropdown,
  NLayout,
  NLayoutContent,
  NLayoutFooter,
  NLayoutHeader,
  NMessageProvider,
  darkTheme,
  dateEnUS,
  dateTrTR,
  enUS,
  trTR,
} from 'naive-ui'
import { PERMISSIONS, UserMenu, useSessionStore } from '@/features/auth'
import DisclaimerNotice from '@/shared/ui/DisclaimerNotice.vue'
import LocaleSwitcher from '@/shared/ui/LocaleSwitcher.vue'

const { t, locale } = useI18n()
const route = useRoute()
const session = useSessionStore()
// Bare pages show no navigation; so does everything while a password change is forced.
const bare = computed(() => Boolean(route.meta.bare) || session.mustChangePassword)
// /reports/:id is still the Reports page.
const reportsActive = computed(() => route.name === 'report')
const router = useRouter()
const adminActive = computed(() => route.path.startsWith('/admin'))
const adminOptions = computed(() =>
  [
    { label: t('nav.users'), key: 'users', permission: PERMISSIONS.userRead },
    { label: t('nav.roles'), key: 'roles', permission: PERMISSIONS.roleManage },
  ].filter((option) => session.can(option.permission)),
)
const preferredDark = usePreferredDark()
const version = __APP_VERSION__

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
          <div class="app__header-row">
            <RouterLink to="/" class="app__brand">{{ t('app.title') }}</RouterLink>
            <nav v-if="!bare && session.user" class="app__nav">
              <RouterLink :to="{ name: 'dashboard' }">{{ t('nav.dashboard') }}</RouterLink>
              <RouterLink v-if="session.can(PERMISSIONS.analysisRead)" :to="{ name: 'analyses' }">{{
                t('nav.analyses')
              }}</RouterLink>
              <RouterLink
                v-if="session.can(PERMISSIONS.analysisRun)"
                :to="{ name: 'new-analysis' }"
                >{{ t('nav.newAnalysis') }}</RouterLink
              >
              <RouterLink
                v-if="session.can(PERMISSIONS.analysisRead)"
                :to="{ name: 'reports' }"
                :class="{ 'router-link-exact-active': reportsActive }"
                >{{ t('nav.reports') }}</RouterLink
              >
              <RouterLink v-if="session.can(PERMISSIONS.settingsRead)" :to="{ name: 'settings' }">{{
                t('nav.settings')
              }}</RouterLink>
              <NDropdown
                v-if="adminOptions.length > 0"
                trigger="click"
                :options="adminOptions"
                @select="(name: string) => router.push({ name })"
              >
                <button
                  type="button"
                  :class="['app__nav-button', { 'app__nav-button--active': adminActive }]"
                >
                  {{ t('nav.admin') }}
                </button>
              </NDropdown>
            </nav>
            <span v-else class="app__nav" />
            <UserMenu v-if="!route.meta.bare" />
            <RouterLink
              v-if="!session.user && route.name !== 'login'"
              :to="{ name: 'login' }"
              class="app__login"
              >{{ t('landing.login') }}</RouterLink
            >
            <LocaleSwitcher />
          </div>
          <DisclaimerNotice compact class="app__disclaimer" />
        </NLayoutHeader>
        <NLayoutContent :class="['app__content', { 'app__content--wide': route.meta.wide }]">
          <RouterView />
        </NLayoutContent>
        <NLayoutFooter class="app__footer">
          <RouterLink :to="{ name: 'about' }">{{ t('nav.about') }}</RouterLink>
          · {{ t('app.version', { version }) }}
        </NLayoutFooter>
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
  padding: 12px 16px;
}

.app__header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.app__disclaimer {
  margin-top: 6px;
}

.app__footer a {
  color: inherit;
}

.app__brand {
  font-weight: 600;
  color: inherit;
  text-decoration: none;
}

.app__login {
  color: inherit;
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

.app__nav-button {
  background: none;
  border: none;
  color: inherit;
  font: inherit;
  cursor: pointer;
  padding: 0;
  opacity: 0.75;
}

.app__nav-button--active {
  opacity: 1;
  font-weight: 600;
}

.app__content {
  padding: 24px 16px;
  max-width: 1100px;
  margin: 0 auto;
}

/* Below the content, not pinned: nothing to do with the page, so it stays out of the way. */
.app__footer {
  padding: 8px 16px 16px;
  text-align: center;
  font-size: 12px;
  opacity: 0.6;
}

/* Pages with side panes (Reports) use the room a wide screen has. */
.app__content--wide {
  max-width: 1600px;
}
</style>
