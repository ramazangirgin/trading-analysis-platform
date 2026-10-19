<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { NCard } from 'naive-ui'
import { PERMISSIONS, useSessionStore } from '@/features/auth'
import { UserManager } from '@/features/users'

const { t } = useI18n()
const session = useSessionStore()
</script>

<template>
  <NCard :title="t('users.title')">
    <UserManager
      :can-manage="session.can(PERMISSIONS.userManage)"
      :current-user-id="session.user?.id ?? ''"
    />
  </NCard>
</template>
