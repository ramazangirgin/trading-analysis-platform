<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NAlert, NCard, NSpace } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { useSessionStore } from '@/features/auth'
import { LockedSection, lockedTitleKey, previewApi, SignUpCallToAction } from '@/features/preview'
import { MarkdownView } from '@/features/reports'
import { ApiError } from '@/shared/api/error'
import type { AnalysisPreview } from '@/shared/api/types'
import { useLabels } from '@/shared/composables/useLabels'
import DisclaimerNotice from '@/shared/ui/DisclaimerNotice.vue'
import RatingTag from '@/shared/ui/RatingTag.vue'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const { te } = useI18n()
const { t, error: errorLabel } = useLabels()

const id = computed(() => String(route.params.id))
const preview = ref<AnalysisPreview | null>(null)
const notFound = ref(false)
const failure = ref<unknown>(null)

const title = (key: string): string => t(lockedTitleKey(key, te))
const allowed = computed(() => Object.entries(preview.value?.sections ?? {}))

async function load() {
  if (session.user) {
    await router.replace({ name: 'analysis', params: { id: id.value } })
    return
  }
  preview.value = null
  notFound.value = false
  failure.value = null
  try {
    preview.value = await previewApi.get(id.value)
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) notFound.value = true
    else failure.value = e
  }
}

watch(id, load, { immediate: true })
</script>

<template>
  <NSpace vertical :size="16" :wrap-item="false">
    <NAlert v-if="failure" type="error" :title="errorLabel(failure)" />

    <NCard v-if="notFound">
      <p>{{ t('preview.notFound') }}</p>
      <RouterLink :to="{ name: 'dashboard' }">{{ t('preview.backToLanding') }}</RouterLink>
    </NCard>

    <template v-if="preview">
      <NCard>
        <NSpace align="center">
          <span class="title">{{ preview.ticker }}</span>
          <span class="subtitle">{{ preview.analysisDate }}</span>
          <RatingTag :rating="preview.rating" size="medium" />
        </NSpace>
      </NCard>

      <NCard v-for="[key, text] in allowed" :key="key" :title="title(key)">
        <MarkdownView :source="text" />
      </NCard>

      <SignUpCallToAction :analysis-id="preview.id" />

      <LockedSection v-for="key in preview.lockedSections" :key="key" :title="title(key)" />

      <SignUpCallToAction v-if="preview.lockedSections.length" :analysis-id="preview.id" />
      <DisclaimerNotice />
    </template>
  </NSpace>
</template>

<style scoped>
.title {
  font-size: 22px;
  font-weight: 700;
}

.subtitle {
  opacity: 0.7;
}
</style>
