<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { NCard } from 'naive-ui'
import { LoginForm, safeRedirect, useSessionStore } from '@/features/auth'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const session = useSessionStore()

function leave() {
  void router.replace(safeRedirect(route.query.redirect))
}

// An open session needs no login.
if (session.user) leave()
</script>

<template>
  <div class="login">
    <NCard :title="t('login.title')" class="login__card">
      <LoginForm @logged-in="leave" />
      <RouterLink :to="{ name: 'about' }" class="login__about">{{ t('nav.about') }}</RouterLink>
      <RouterLink :to="{ name: 'dashboard' }" class="login__about login__about--next">{{
        t('login.backToLanding')
      }}</RouterLink>
    </NCard>
  </div>
</template>

<style scoped>
.login {
  display: flex;
  justify-content: center;
  padding-top: 48px;
}

.login__card {
  max-width: 400px;
}

.login__about {
  display: inline-block;
  margin-top: 16px;
  font-size: 13px;
  color: inherit;
}

.login__about--next {
  margin-left: 16px;
}
</style>
