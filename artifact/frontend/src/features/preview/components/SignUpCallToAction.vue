<script setup lang="ts">
import { NButton, NCard } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { loginLocation } from '../model/loginTarget'

defineProps<{ analysisId: string }>()
const { t } = useI18n()
</script>

<template>
  <NCard class="cta" embedded>
    <h3 class="cta__title">{{ t('preview.callToActionTitle') }}</h3>
    <p class="cta__text">{{ t('preview.callToActionText') }}</p>
    <RouterLink :to="loginLocation(analysisId)" custom v-slot="{ href, navigate }">
      <NButton tag="a" type="primary" :href="href" @click="navigate">{{
        t('preview.login')
      }}</NButton>
    </RouterLink>
  </NCard>
</template>

<style scoped>
.cta {
  text-align: center;
}

.cta__title {
  margin: 0 0 8px;
}

.cta__text {
  margin: 0 0 16px;
}
</style>
