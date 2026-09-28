<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { NButton, NResult } from 'naive-ui'

const { t } = useI18n()
</script>

<template>
  <NResult status="404" :title="t('notFound.heading')">
    <template #footer>
      <RouterLink to="/" custom v-slot="{ navigate }">
        <NButton @click="navigate">{{ t('notFound.back') }}</NButton>
      </RouterLink>
    </template>
  </NResult>
</template>
