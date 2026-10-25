<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { NAlert, NButton, NSpace } from 'naive-ui'
import { PreviewCard, previewApi } from '@/features/preview'
import type { AnalysisPreviewSummary } from '@/shared/api/types'
import { useLabels } from '@/shared/composables/useLabels'

const PAGE_SIZE = 12

const { t, error: errorLabel } = useLabels()
const items = ref<AnalysisPreviewSummary[]>([])
const hasMore = ref(false)
const loading = ref(false)
const failure = ref<unknown>(null)
let page = 0

async function loadNext() {
  loading.value = true
  try {
    const result = await previewApi.list(page, PAGE_SIZE)
    items.value.push(...result.items)
    hasMore.value = result.hasMore
    page += 1
    failure.value = null
  } catch (e) {
    failure.value = e
  } finally {
    loading.value = false
  }
}

onMounted(loadNext)
</script>

<template>
  <NSpace vertical :size="24" :wrap-item="false">
    <section class="hero">
      <h1 class="hero__headline">{{ t('landing.headline') }}</h1>
      <p class="hero__description">{{ t('landing.description') }}</p>
      <RouterLink :to="{ name: 'login' }" custom v-slot="{ href, navigate }">
        <NButton tag="a" type="primary" size="large" :href="href" @click="navigate">{{
          t('landing.login')
        }}</NButton>
      </RouterLink>
    </section>

    <NAlert v-if="failure" type="error" :title="errorLabel(failure)" />

    <div v-if="items.length" class="cards">
      <PreviewCard v-for="item in items" :key="item.id" :analysis="item" />
    </div>
    <p v-else-if="!loading && !failure" class="empty">{{ t('landing.empty') }}</p>

    <div v-if="hasMore" class="more">
      <NButton secondary :loading="loading" @click="loadNext">{{ t('landing.showMore') }}</NButton>
    </div>
  </NSpace>
</template>

<style scoped>
.hero {
  text-align: center;
  padding: 24px 0;
}

.hero__headline {
  margin: 0 0 12px;
}

.hero__description {
  max-width: 640px;
  margin: 0 auto 20px;
  opacity: 0.8;
}

.cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

.empty {
  text-align: center;
  opacity: 0.7;
}

.more {
  text-align: center;
}
</style>
