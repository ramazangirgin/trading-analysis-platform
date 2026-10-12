<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { NDropdown } from 'naive-ui'
import { useSessionStore } from '../store'

const { t } = useI18n()
const session = useSessionStore()

// One action for now; the change-password entry fits in beside it later.
const options = computed(() => [{ label: t('nav.logout'), key: 'logout' }])

function onSelect(key: string) {
  if (key === 'logout') void session.logout()
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
