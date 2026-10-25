<script setup lang="ts">
import { NCard } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import type { AnalysisPreviewSummary } from '@/shared/api/types'
import RatingTag from '@/shared/ui/RatingTag.vue'

defineProps<{ analysis: AnalysisPreviewSummary }>()
const { t } = useI18n()
</script>

<template>
  <RouterLink :to="{ name: 'preview', params: { id: analysis.id } }" class="card-link">
    <NCard hoverable size="small" class="card">
      <div class="card__head">
        <strong class="card__ticker">{{ analysis.ticker }}</strong>
        <span class="card__date">{{ analysis.analysisDate }}</span>
        <RatingTag :rating="analysis.rating" />
      </div>
      <div class="card__excerpt">
        <p>{{ analysis.excerpt }}</p>
        <div class="card__fade">
          <span class="card__lock">{{ t('landing.lockedHint') }}</span>
        </div>
      </div>
    </NCard>
  </RouterLink>
</template>

<style scoped>
.card-link {
  color: inherit;
  text-decoration: none;
}

.card {
  height: 100%;
}

.card__head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.card__date {
  opacity: 0.7;
  font-size: 13px;
  flex: 1;
}

.card__excerpt {
  position: relative;
  max-height: 110px;
  overflow: hidden;
  margin-top: 8px;
}

.card__excerpt p {
  margin: 0;
  overflow-wrap: anywhere;
}

.card__fade {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  padding-bottom: 4px;
  background: linear-gradient(to bottom, transparent 10%, var(--n-color, #fff) 85%);
}

.card__lock {
  font-size: 13px;
  font-weight: 600;
}
</style>
