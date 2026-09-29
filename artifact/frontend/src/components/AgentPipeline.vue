<script setup lang="ts">
import { NTag } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { PIPELINE, type AgentStatus } from '@/domain/runView'
import { useLabels } from '@/composables/useLabels'

/** Upstream's pipeline, stage by stage, with each agent's live status. */
const props = defineProps<{ agents: Record<string, AgentStatus> }>()
const { t } = useI18n()
const { agent } = useLabels()

const types = {
  pending: 'default',
  in_progress: 'info',
  completed: 'success',
  error: 'error',
} as const

const visible = (name: string) => name in props.agents
</script>

<template>
  <div class="pipeline">
    <section v-for="stage in PIPELINE" :key="stage.stage" class="pipeline__stage">
      <h4 class="pipeline__title">{{ t(`stages.${stage.stage}`) }}</h4>
      <template v-for="name in stage.agents" :key="name">
        <NTag
          v-if="visible(name)"
          :type="types[agents[name] ?? 'pending']"
          :bordered="agents[name] === 'in_progress'"
          size="small"
          class="pipeline__agent"
        >
          {{ agent(name) }} · {{ t(`agentStatus.${agents[name] ?? 'pending'}`) }}
        </NTag>
      </template>
    </section>
  </div>
</template>

<style scoped>
.pipeline {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));
  gap: 12px;
}

.pipeline__stage {
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: flex-start;
}

.pipeline__title {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  opacity: 0.75;
}

.pipeline__agent {
  max-width: 100%;
}
</style>
