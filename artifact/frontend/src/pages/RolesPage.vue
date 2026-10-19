<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { NCard } from 'naive-ui'
import { RoleManager } from '@/features/roles'

const { t } = useI18n()
</script>

<template>
  <NCard :title="t('roles.title')">
    <RoleManager />
  </NCard>
</template>
