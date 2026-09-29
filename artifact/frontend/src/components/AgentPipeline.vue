<script setup lang="ts">
import { computed } from 'vue'
import { useThemeVars } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { PIPELINE, stageStatus, type AgentStatus, type PipelineStage } from '@/domain/runView'
import { useLabels } from '@/composables/useLabels'

/**
 * Upstream's pipeline as a flow, like the diagrams in the TradingAgents README: one box per team,
 * arrows between them, one card per agent with its live status. Only agents the run reported are
 * shown, so analysts that were not selected leave no gap.
 */
const props = defineProps<{ agents: Record<string, AgentStatus> }>()
const { t } = useI18n()
const { agent } = useLabels()
const vars = useThemeVars()

// Line icons (24×24, stroked with currentColor) for each team.
const ICONS: Record<PipelineStage['stage'], string> = {
  analysts: 'M3 21h18M6 17v-6M11 17V5M16 17v-9M21 17v-3',
  research: 'M12 3v18M7 21h10M4 7h16M7 7l-3 7a3 3 0 0 0 6 0zM17 7l-3 7a3 3 0 0 0 6 0z',
  trading: 'M4 8h14l-4-4M20 16H6l4 4',
  risk: 'M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z',
  portfolio: 'M3 8h18v12H3zM8 8V5h8v3M3 13h18',
}

const shown = (names: string[] | undefined) => (names ?? []).filter((name) => name in props.agents)

const stages = computed(() =>
  PIPELINE.map((stage) => {
    const agents = shown(stage.agents)
    const debaters = shown(stage.debaters)
    return {
      ...stage,
      agents,
      debaters,
      // Agents outside the debate (analysts, trader, portfolio manager) are listed plainly.
      others: agents.filter((name) => !debaters.includes(name) && name !== stage.judge),
      judge: stage.judge && stage.judge in props.agents ? stage.judge : null,
      status: stageStatus(props.agents, stage.agents),
      done: agents.filter((name) => props.agents[name] === 'completed').length,
    }
  }).filter((stage) => stage.agents.length),
)

const statusOf = (name: string): AgentStatus => props.agents[name] ?? 'pending'
</script>

<template>
  <ol
    class="flow"
    :style="{
      '--c-border': vars.borderColor,
      '--c-muted': vars.textColor3,
      '--c-surface': vars.actionColor,
      '--c-info': vars.infoColor,
      '--c-success': vars.successColor,
      '--c-error': vars.errorColor,
    }"
  >
    <template v-for="(stage, index) in stages" :key="stage.stage">
      <li v-if="index > 0" class="flow__arrow" aria-hidden="true">
        <svg viewBox="0 0 24 24"><path d="M4 12h15m-5-5 5 5-5 5" /></svg>
      </li>
      <li class="team" :class="`team--${stage.status}`">
        <header class="team__header">
          <svg class="team__icon" viewBox="0 0 24 24" aria-hidden="true">
            <path :d="ICONS[stage.stage]" />
          </svg>
          <h4 class="team__title">{{ t(`stages.${stage.stage}`) }}</h4>
          <span class="team__count">{{ stage.done }}/{{ stage.agents.length }}</span>
        </header>

        <div class="team__body">
          <div
            v-for="name in stage.others"
            :key="name"
            class="agent"
            :class="`agent--${statusOf(name)}`"
          >
            <span class="agent__dot" />
            <span class="agent__name">{{ agent(name) }}</span>
            <span class="agent__status">{{ t(`agentStatus.${statusOf(name)}`) }}</span>
          </div>

          <div v-if="stage.debaters.length" class="debate">
            <template v-for="(name, i) in stage.debaters" :key="name">
              <span v-if="i > 0" class="debate__swap" :title="t('pipeline.debate')">⇄</span>
              <div class="agent" :class="`agent--${statusOf(name)}`">
                <span class="agent__dot" />
                <span class="agent__name">{{ agent(name) }}</span>
                <span class="agent__status">{{ t(`agentStatus.${statusOf(name)}`) }}</span>
              </div>
            </template>
          </div>

          <template v-if="stage.judge">
            <span class="debate__verdict" aria-hidden="true">↓</span>
            <div class="agent agent--judge" :class="`agent--${statusOf(stage.judge)}`">
              <span class="agent__dot" />
              <span class="agent__name">{{ agent(stage.judge) }}</span>
              <span class="agent__status">{{ t(`agentStatus.${statusOf(stage.judge)}`) }}</span>
            </div>
          </template>
        </div>
      </li>
    </template>
  </ol>
</template>

<style scoped>
.flow {
  display: flex;
  align-items: stretch;
  gap: 4px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.flow__arrow {
  display: flex;
  align-items: center;
  flex: none;
  color: var(--c-muted);
}

.flow__arrow svg {
  width: 20px;
  height: 20px;
  fill: none;
  stroke: currentColor;
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.team {
  flex: 1 1 0;
  min-width: 0;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--c-border);
  border-radius: 8px;
  overflow: hidden;
}

.team--in_progress {
  border-color: var(--c-info);
}

.team--error {
  border-color: var(--c-error);
}

.team__header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  background: var(--c-surface);
}

.team__icon {
  flex: none;
  width: 18px;
  height: 18px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.8;
  stroke-linecap: round;
  stroke-linejoin: round;
  color: var(--c-muted);
}

.team--in_progress .team__icon {
  color: var(--c-info);
}

.team--completed .team__icon {
  color: var(--c-success);
}

.team--error .team__icon {
  color: var(--c-error);
}

.team__title {
  flex: 1;
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.team__count {
  font-size: 12px;
  color: var(--c-muted);
  font-variant-numeric: tabular-nums;
}

.team__body {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px;
}

.debate {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.debate__swap,
.debate__verdict {
  align-self: center;
  font-size: 13px;
  line-height: 1;
  color: var(--c-muted);
}

.agent {
  display: grid;
  grid-template-columns: auto 1fr;
  column-gap: 8px;
  align-items: center;
  padding: 6px 8px;
  border: 1px solid var(--c-border);
  border-radius: 6px;
}

.agent--judge {
  border-style: dashed;
}

.agent__dot {
  grid-row: span 2;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  border: 2px solid var(--c-muted);
  box-sizing: border-box;
}

.agent__name {
  font-size: 13px;
  line-height: 1.3;
  overflow-wrap: anywhere;
}

.agent__status {
  font-size: 11px;
  color: var(--c-muted);
}

.agent--in_progress {
  border-color: var(--c-info);
}

.agent--in_progress .agent__dot {
  border-color: var(--c-info);
  background: var(--c-info);
  animation: pulse 1.4s ease-in-out infinite;
}

.agent--in_progress .agent__status {
  color: var(--c-info);
}

.agent--completed .agent__dot {
  border-color: var(--c-success);
  background: var(--c-success);
}

.agent--error {
  border-color: var(--c-error);
}

.agent--error .agent__dot {
  border-color: var(--c-error);
  background: var(--c-error);
}

.agent--error .agent__status {
  color: var(--c-error);
}

@keyframes pulse {
  50% {
    box-shadow: 0 0 0 5px transparent;
    opacity: 0.45;
  }
}

@media (prefers-reduced-motion: reduce) {
  .agent--in_progress .agent__dot {
    animation: none;
  }
}

/* Narrow screens: the flow runs top to bottom. */
@media (max-width: 860px) {
  .flow {
    flex-direction: column;
  }

  .team {
    flex: none;
  }

  .flow__arrow {
    justify-content: center;
    transform: rotate(90deg);
  }
}
</style>
