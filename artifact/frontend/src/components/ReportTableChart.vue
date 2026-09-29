<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { BarSeriesOption, ChartOption, LineSeriesOption } from '@/charts/echarts'
import { useChart, useChartTheme } from '@/charts/useChart'
import type { TableChart } from '@/domain/tableChart'

/**
 * A chart of one report table (KI-4). The table stays beside it as the exact, accessible view;
 * the chart only makes the trend visible. One series → bars, several → lines.
 */
const props = defineProps<{ chart: TableChart }>()
const { locale } = useI18n()
const theme = useChartTheme()
const el = ref<HTMLDivElement | null>(null)

const format = (value: number | null | undefined) => {
  if (value === null || value === undefined) return '—'
  const { prefix, suffix } = props.chart.unit
  const number = new Intl.NumberFormat(locale.value, { maximumFractionDigits: 2 }).format(value)
  return `${prefix}${number}${suffix && suffix !== '%' ? ' ' : ''}${suffix}`
}

const option = computed<ChartOption>(() => {
  const { periods, series, visible } = props.chart
  const c = theme.value
  const bars = series.length === 1
  return {
    color: c.series,
    animation: false,
    textStyle: { fontFamily: c.font },
    grid: { left: 8, right: 16, top: series.length > 1 ? 36 : 12, bottom: 8, containLabel: true },
    legend:
      series.length > 1
        ? {
            type: 'scroll',
            top: 0,
            left: 0,
            icon: 'roundRect',
            pageTextStyle: { color: c.muted },
            pageIconColor: c.text,
            pageIconInactiveColor: c.border,
            itemWidth: 12,
            itemHeight: 4,
            textStyle: { color: c.text, fontSize: 12 },
            // Switched-off series must look off in both themes (ECharts' default is a light grey).
            inactiveColor: c.disabled,
            inactiveBorderColor: c.disabled,
            selected: Object.fromEntries(series.map((s, i) => [s.name, i < visible])),
          }
        : undefined,
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: bars ? 'shadow' : 'line', lineStyle: { color: c.muted } },
      backgroundColor: c.popover,
      borderColor: c.border,
      textStyle: { color: c.strong, fontSize: 12 },
      valueFormatter: (value) => format(value as number | null),
    },
    xAxis: {
      type: 'category',
      data: periods,
      boundaryGap: bars,
      axisLine: { lineStyle: { color: c.border } },
      axisTick: { show: false },
      axisLabel: { color: c.muted, fontSize: 11, hideOverlap: true },
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: c.divider } },
      axisLabel: {
        color: c.muted,
        fontSize: 11,
        formatter: (value: number) =>
          new Intl.NumberFormat(locale.value, { notation: 'compact' }).format(value),
      },
    },
    series: series.map((s) =>
      bars
        ? ({
            type: 'bar',
            name: s.name,
            barMaxWidth: 28,
            // Rounded at the data end only; the baseline end stays square.
            data: s.values.map((value) => ({
              value,
              itemStyle: { borderRadius: (value ?? 0) < 0 ? [0, 0, 4, 4] : [4, 4, 0, 0] },
            })),
          } satisfies BarSeriesOption)
        : ({
            type: 'line',
            name: s.name,
            data: s.values,
            connectNulls: true,
            lineStyle: { width: 2 },
            symbol: 'circle',
            symbolSize: 8,
            itemStyle: { borderColor: c.surface, borderWidth: 2 },
            emphasis: { focus: 'series' },
          } satisfies LineSeriesOption),
    ),
  }
})

useChart(el, option)

const summary = computed(
  () =>
    props.chart.series.map((s) => s.name).join(', ') +
    ` · ${props.chart.periods[0]} – ${props.chart.periods.at(-1)}`,
)
</script>

<template>
  <figure class="table-chart">
    <figcaption v-if="chart.label" class="table-chart__unit">{{ chart.label }}</figcaption>
    <div ref="el" class="table-chart__plot" role="img" :aria-label="summary" />
  </figure>
</template>

<style scoped>
.table-chart {
  margin: 12px 0;
}

.table-chart__unit {
  font-size: 12px;
  opacity: 0.7;
}

.table-chart__plot {
  width: 100%;
  height: 260px;
}
</style>
