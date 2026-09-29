<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  NAlert,
  NButton,
  NCard,
  NCollapse,
  NCollapseItem,
  NDescriptions,
  NDescriptionsItem,
  NEmpty,
  NPopconfirm,
  NSpace,
  NStatistic,
  NTabPane,
  NTabs,
  NTag,
} from 'naive-ui'
import { ACTIVE_STATUSES, api, type Analysis } from '@/api/client'
import { SECTIONS } from '@/domain/runView'
import { useRunStream } from '@/composables/useRunStream'
import { useLabels } from '@/composables/useLabels'
import { usePolling } from '@/composables/usePolling'
import AgentPipeline from '@/components/AgentPipeline.vue'
import MarkdownView from '@/components/MarkdownView.vue'
import RatingTag from '@/components/RatingTag.vue'
import StatusTag from '@/components/StatusTag.vue'

const route = useRoute()
const router = useRouter()
const { t, error: errorLabel, errorCode, dateTime, duration, integer, usd } = useLabels()

const id = computed(() => String(route.params.id))
const analysis = ref<Analysis | null>(null)
const failure = ref<unknown>(null)
const acting = ref(false)
const { view, connected, ended } = useRunStream(id)

const active = computed(() => !!analysis.value && ACTIVE_STATUSES.includes(analysis.value.status))
const rating = computed(() => analysis.value?.rating ?? view.decision?.rating ?? null)
const sections = computed(() => SECTIONS.filter((key) => view.sections[key]))
const feed = computed(() => [...view.feed].reverse())
const debates = computed(() => ({
  investment: view.debates.filter((turn) => turn.debate === 'investment'),
  risk: view.debates.filter((turn) => turn.debate === 'risk'),
}))
const stats = computed(() => {
  const s = analysis.value?.stats
  const live = view.stats
  return {
    llmCalls: live?.llmCalls ?? s?.llmCalls ?? null,
    toolCalls: live?.toolCalls ?? s?.toolCalls ?? null,
    tokensIn: live?.tokensIn ?? s?.tokensIn ?? null,
    tokensOut: live?.tokensOut ?? s?.tokensOut ?? null,
    costUsd: live?.costUsd ?? s?.costUsd ?? null,
    elapsedMs: live?.elapsedS != null ? live.elapsedS * 1000 : (s?.elapsedMs ?? null),
  }
})

async function refresh() {
  try {
    analysis.value = await api.getAnalysis(id.value)
    failure.value = null
  } catch (e) {
    failure.value = e
  }
}

usePolling(refresh, () => active.value)
watch(ended, (isEnded) => isEnded && refresh())
watch(id, refresh)

async function stop() {
  acting.value = true
  try {
    analysis.value = await api.stopAnalysis(id.value)
  } catch (e) {
    failure.value = e
  } finally {
    acting.value = false
  }
}

async function rerun() {
  acting.value = true
  try {
    const next = await api.rerunAnalysis(id.value)
    await router.push({ name: 'analysis', params: { id: next.id } })
  } catch (e) {
    failure.value = e
  } finally {
    acting.value = false
  }
}
</script>

<template>
  <NSpace vertical :size="16">
    <NAlert v-if="failure" type="error" :title="errorLabel(failure)" />

    <NCard v-if="analysis">
      <template #header>
        <NSpace align="center" :wrap="true">
          <span class="title">{{ analysis.spec.ticker }}</span>
          <span class="subtitle">{{ analysis.spec.tradeDate }}</span>
          <StatusTag :status="analysis.status" />
          <RatingTag :rating="rating" size="medium" />
        </NSpace>
      </template>
      <template #header-extra>
        <NSpace>
          <NPopconfirm v-if="active" @positive-click="stop">
            <template #trigger>
              <NButton type="error" secondary :loading="acting">{{ t('detail.stop') }}</NButton>
            </template>
            {{ t('detail.stopConfirm') }}
          </NPopconfirm>
          <NButton v-else secondary :loading="acting" @click="rerun">{{
            t('detail.rerun')
          }}</NButton>
        </NSpace>
      </template>

      <NSpace vertical :size="16">
        <NAlert
          v-if="analysis.status === 'FAILED'"
          type="error"
          :title="`${t('detail.failed')}: ${errorCode(analysis.errorCode, null)}`"
        >
          {{ analysis.errorMessage }}
        </NAlert>
        <NAlert
          v-if="active && !connected && !ended"
          type="warning"
          :title="t('detail.reconnecting')"
        />
        <AgentPipeline :agents="view.agents" />
      </NSpace>
    </NCard>

    <NCard
      v-if="view.sections.final_trade_decision || analysis?.decision"
      :title="t('detail.decision')"
    >
      <MarkdownView :source="view.sections.final_trade_decision ?? analysis?.decision ?? ''" />
    </NCard>

    <NCard>
      <NTabs type="line" animated>
        <NTabPane name="feed" :tab="t('detail.feed')">
          <NEmpty
            v-if="!feed.length"
            :description="active ? t('detail.waiting') : t('detail.noFeed')"
          />
          <ol v-else class="feed">
            <li v-for="item in feed" :key="item.seq" class="feed__item">
              <div class="feed__meta">
                <NTag
                  size="tiny"
                  :bordered="false"
                  :type="item.level === 'ERROR' || item.level === 'WARNING' ? 'warning' : 'default'"
                >
                  {{ item.kind }}
                </NTag>
                <strong>{{ item.title }}</strong>
                <span class="feed__time">{{ dateTime(item.timestamp) }}</span>
              </div>
              <pre class="feed__text">{{ item.text }}</pre>
            </li>
          </ol>
        </NTabPane>

        <NTabPane name="reports" :tab="t('detail.reports')">
          <NEmpty v-if="!sections.length" :description="t('detail.noReports')" />
          <NCollapse v-else :default-expanded-names="[sections[0]]">
            <NCollapseItem
              v-for="key in sections"
              :key="key"
              :name="key"
              :title="t(`sections.${key}`)"
            >
              <MarkdownView :source="view.sections[key] ?? ''" />
            </NCollapseItem>
          </NCollapse>
        </NTabPane>

        <NTabPane name="debates" :tab="t('detail.debates')">
          <NEmpty v-if="!view.debates.length" :description="t('detail.noDebates')" />
          <template v-for="kind in ['investment', 'risk'] as const" :key="kind">
            <section v-if="debates[kind].length" class="debate">
              <h3>
                {{ t(kind === 'investment' ? 'detail.investmentDebate' : 'detail.riskDebate') }}
              </h3>
              <article
                v-for="turn in debates[kind]"
                :key="turn.seq"
                :class="['debate__turn', `debate__turn--${turn.speaker}`]"
              >
                <header class="debate__speaker">
                  {{ t(`speakers.${turn.speaker}`) }}
                  <span v-if="turn.round" class="feed__time">{{
                    t('detail.round', { round: turn.round })
                  }}</span>
                </header>
                <MarkdownView :source="turn.content" />
              </article>
            </section>
          </template>
        </NTabPane>

        <NTabPane name="stats" :tab="t('detail.stats')">
          <div class="stats">
            <NStatistic :label="t('detail.llmCalls')" :value="integer(stats.llmCalls)" />
            <NStatistic :label="t('detail.toolCalls')" :value="integer(stats.toolCalls)" />
            <NStatistic :label="t('detail.tokensIn')" :value="integer(stats.tokensIn)" />
            <NStatistic :label="t('detail.tokensOut')" :value="integer(stats.tokensOut)" />
            <NStatistic :label="t('detail.cost')" :value="usd(stats.costUsd)" />
            <NStatistic :label="t('detail.elapsed')" :value="duration(stats.elapsedMs)" />
          </div>
          <NDescriptions
            v-if="analysis"
            :title="t('detail.spec')"
            :column="2"
            label-placement="left"
            bordered
            size="small"
            class="spec"
          >
            <NDescriptionsItem :label="t('form.provider')">{{
              analysis.spec.llmProvider
            }}</NDescriptionsItem>
            <NDescriptionsItem :label="t('form.deepModel')">{{
              analysis.spec.deepThinkLlm
            }}</NDescriptionsItem>
            <NDescriptionsItem :label="t('form.quickModel')">{{
              analysis.spec.quickThinkLlm
            }}</NDescriptionsItem>
            <NDescriptionsItem :label="t('form.analysts')">
              {{ analysis.spec.analysts.map((a) => t(`analysts.${a}`)).join(', ') }}
            </NDescriptionsItem>
            <NDescriptionsItem :label="t('form.debateRounds')">{{
              analysis.spec.maxDebateRounds
            }}</NDescriptionsItem>
            <NDescriptionsItem :label="t('form.riskRounds')">{{
              analysis.spec.maxRiskDiscussRounds
            }}</NDescriptionsItem>
            <NDescriptionsItem :label="t('form.outputLanguage')">{{
              analysis.spec.outputLanguage
            }}</NDescriptionsItem>
            <NDescriptionsItem :label="t('runs.created')">{{
              dateTime(analysis.createdAt)
            }}</NDescriptionsItem>
          </NDescriptions>
        </NTabPane>
      </NTabs>
    </NCard>
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

.feed {
  list-style: none;
  margin: 0;
  padding: 0;
  max-height: 560px;
  overflow-y: auto;
}

.feed__item {
  padding: 8px 0;
  border-bottom: 1px solid rgba(128, 128, 128, 0.2);
}

.feed__meta {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

.feed__time {
  font-size: 12px;
  opacity: 0.6;
  margin-left: 8px;
}

.feed__text {
  margin: 4px 0 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  font-size: 13px;
  max-height: 240px;
  overflow-y: auto;
}

.debate h3 {
  margin: 16px 0 8px;
}

.debate__turn {
  border-left: 3px solid rgba(128, 128, 128, 0.4);
  padding: 4px 12px;
  margin-bottom: 12px;
}

.debate__turn--bull,
.debate__turn--aggressive {
  border-left-color: #18a058;
}

.debate__turn--bear,
.debate__turn--conservative {
  border-left-color: #d03050;
}

.debate__turn--judge {
  border-left-color: #2080f0;
}

.debate__speaker {
  font-weight: 600;
}

.stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}

.spec {
  overflow-x: auto;
}
</style>
