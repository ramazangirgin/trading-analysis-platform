<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { NRadioButton, NRadioGroup } from 'naive-ui'
import { api, type PriceHistory } from '@/api/client'
import type { ChartOption } from '@/charts/echarts'
import { useChart, useChartTheme } from '@/charts/useChart'

/**
 * The price chart above the market report (KI-4): close with the moving averages the market
 * analyst quotes, volume below on its own axis, from upstream's price cache. Nothing is shown when
 * the cache has no prices for the analysis.
 */
const props = defineProps<{ analysisId: string }>()
const { t, locale } = useI18n()
const theme = useChartTheme()

const history = ref<PriceHistory | null>(null)
watch(
  () => props.analysisId,
  async (id) => {
    history.value = await api.getPrices(id).catch(() => null)
  },
  { immediate: true },
)

// Trading days per range; the backend sends about a year.
const RANGES = { '3m': 63, '6m': 126, '1y': 252 } as const
const range = ref<keyof typeof RANGES>('6m')
const points = computed(() => history.value?.points.slice(-RANGES[range.value]) ?? [])
const lastDate = computed(() => points.value.at(-1)?.date ?? '')

const price = (value: unknown) =>
  typeof value === 'number'
    ? new Intl.NumberFormat(locale.value, {
        maximumFractionDigits: 2,
        minimumFractionDigits: 2,
      }).format(value)
    : '—'
const compact = (value: unknown) =>
  typeof value === 'number'
    ? new Intl.NumberFormat(locale.value, { notation: 'compact' }).format(value)
    : '—'
const shortDate = (iso: string) =>
  new Date(`${iso}T12:00:00`).toLocaleDateString(locale.value, { day: 'numeric', month: 'short' })

const el = ref<HTMLDivElement | null>(null)
const option = computed<ChartOption>(() => {
  const c = theme.value
  const dates = points.value.map((p) => p.date)
  const axis = (gridIndex: number, labels: boolean) => ({
    type: 'category' as const,
    gridIndex,
    data: dates,
    boundaryGap: true,
    axisLine: { lineStyle: { color: c.border } },
    axisTick: { show: false },
    axisLabel: {
      show: labels,
      color: c.muted,
      fontSize: 11,
      hideOverlap: true,
      formatter: shortDate,
    },
  })
  const line = (name: string, key: 'close' | 'ema10' | 'sma50' | 'sma200') => ({
    type: 'line' as const,
    name,
    data: points.value.map((p) => p[key]),
    showSymbol: false,
    lineStyle: { width: 2 },
    emphasis: { focus: 'series' as const },
    tooltip: { valueFormatter: price },
  })
  return {
    color: c.series,
    animation: false,
    textStyle: { fontFamily: c.font },
    legend: {
      top: 0,
      left: 0,
      icon: 'roundRect',
      itemWidth: 12,
      itemHeight: 4,
      textStyle: { color: c.text, fontSize: 12 },
      inactiveColor: c.disabled,
      inactiveBorderColor: c.disabled,
      data: [t('prices.close'), '10 EMA', '50 SMA', '200 SMA'],
    },
    axisPointer: { link: [{ xAxisIndex: 'all' }] },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'line', lineStyle: { color: c.muted } },
      backgroundColor: c.popover,
      borderColor: c.border,
      textStyle: { color: c.strong, fontSize: 12 },
    },
    grid: [
      { left: 8, right: 16, top: 32, height: 210, containLabel: true },
      { left: 8, right: 16, top: 262, height: 56, containLabel: true },
    ],
    xAxis: [axis(0, false), axis(1, true)],
    yAxis: [
      {
        type: 'value',
        gridIndex: 0,
        scale: true,
        splitLine: { lineStyle: { color: c.divider } },
        axisLabel: { color: c.muted, fontSize: 11, formatter: compact },
      },
      {
        type: 'value',
        gridIndex: 1,
        splitNumber: 1,
        splitLine: { show: false },
        axisLabel: { color: c.muted, fontSize: 11, formatter: compact },
      },
    ],
    series: [
      {
        ...line(t('prices.close'), 'close'),
        markLine: {
          symbol: 'none',
          silent: true,
          lineStyle: { color: c.muted, type: 'dashed', width: 1 },
          label: { show: false },
          data: [{ xAxis: lastDate.value }],
        },
      },
      line('10 EMA', 'ema10'),
      line('50 SMA', 'sma50'),
      line('200 SMA', 'sma200'),
      {
        type: 'bar',
        name: t('prices.volume'),
        xAxisIndex: 1,
        yAxisIndex: 1,
        data: points.value.map((p) => p.volume),
        itemStyle: { color: c.muted, opacity: 0.45, borderRadius: [2, 2, 0, 0] },
        tooltip: { valueFormatter: compact },
      },
    ],
  }
})
useChart(el, option)
</script>

<template>
  <figure v-if="history && points.length" class="price-chart">
    <div class="price-chart__header">
      <span class="price-chart__title">
        {{ t('prices.title', { ticker: history.ticker }) }}
        <span class="price-chart__last">{{
          t('prices.lastData', { date: shortDate(lastDate) })
        }}</span>
      </span>
      <NRadioGroup v-model:value="range" size="small">
        <NRadioButton v-for="(_, key) in RANGES" :key="key" :value="key">
          {{ t(`prices.range.${key}`) }}
        </NRadioButton>
      </NRadioGroup>
    </div>
    <div
      ref="el"
      class="price-chart__plot"
      role="img"
      :aria-label="t('prices.title', { ticker: history.ticker })"
    />
    <figcaption class="price-chart__source">{{ t('prices.source') }}</figcaption>
  </figure>
</template>

<style scoped>
.price-chart {
  margin: 0 0 16px;
}

.price-chart__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}

.price-chart__last {
  margin-left: 8px;
  font-size: 12px;
  font-weight: 400;
  opacity: 0.7;
}

.price-chart__title {
  font-weight: 600;
}

.price-chart__plot {
  width: 100%;
  height: 330px;
}

.price-chart__source {
  margin: 4px 0 0;
  font-size: 12px;
  opacity: 0.7;
}
</style>
