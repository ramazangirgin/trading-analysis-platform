<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { NDropdown, useMessage } from 'naive-ui'
import { useLabels } from '@/shared/composables/useLabels'
import { useSessionStore } from '../store'

const { t, error: errorText } = useLabels()
const session = useSessionStore()
const message = useMessage()
const router = useRouter()

// While the password change is forced, logging out is the only other way.
const options = computed(() => [
  ...(session.mustChangePassword
    ? []
    : [{ label: t('nav.changePassword'), key: 'change-password' }]),
  { label: t('nav.logout'), key: 'logout' },
])

async function onSelect(key: string) {
  if (key === 'change-password') {
    void router.push({ name: 'change-password' })
    return
  }
  if (key !== 'logout') return
  try {
    await session.logout()
  } catch (e) {
    message.error(errorText(e))
  }
}
</script>

<template>
  <NDropdown v-if="session.user" trigger="click" :options="options" @select="onSelect">
    <button type="button" class="user-menu">{{ session.username }}</button>
  </NDropdown>
</template>

<style scoped>
.user-menu {
  background: none;
  border: none;
  color: inherit;
  font: inherit;
  cursor: pointer;
  padding: 0;
}
</style>
