<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { NAlert, NCard, useMessage } from 'naive-ui'
import { ChangePasswordForm, safeRedirect, useSessionStore } from '@/features/auth'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const message = useMessage()
const session = useSessionStore()

// Forced until the first change: the notice stays on the page after the flag is cleared, so the
// success message and the redirect do not flicker it away.
const forced = session.mustChangePassword

function changed() {
  message.success(t('password.changed'))
  void router.replace(safeRedirect(route.query.redirect))
}
</script>

<template>
  <div class="password">
    <NCard :title="t('password.title')" class="password__card">
      <NAlert v-if="forced" type="warning" style="margin-bottom: 16px">
        {{ t('password.forced') }}
      </NAlert>
      <ChangePasswordForm @changed="changed" />
    </NCard>
  </div>
</template>

<style scoped>
.password {
  display: flex;
  justify-content: center;
  padding-top: 48px;
}

.password__card {
  max-width: 400px;
}
</style>
