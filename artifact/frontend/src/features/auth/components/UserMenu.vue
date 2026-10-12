<script setup lang="ts">
import { computed } from 'vue'
import { NDropdown, useMessage } from 'naive-ui'
import { useLabels } from '@/shared/composables/useLabels'
import { useSessionStore } from '../store'

const { t, error: errorText } = useLabels()
const session = useSessionStore()
const message = useMessage()

// One action for now; the change-password entry fits in beside it later.
const options = computed(() => [{ label: t('nav.logout'), key: 'logout' }])

async function onSelect(key: string) {
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
