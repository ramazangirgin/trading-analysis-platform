<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useThemeVars } from 'naive-ui'
import { usePreferredDark, useResizeObserver } from '@vueuse/core'
import { init, use, type ComposeOption, type ECharts } from 'echarts/core'
import { BarChart, LineChart, type BarSeriesOption, type LineSeriesOption } from 'echarts/charts'
import {
  GridComponent,
  LegendComponent,
  TooltipComponent,
  type GridComponentOption,
  type LegendComponentOption,
  type TooltipComponentOption,
} from 'echarts/components'
import { SVGRenderer } from 'echarts/renderers'
import type { TableChart } from '@/domain/tableChart'

use([BarChart, LineChart, GridComponent, LegendComponent, TooltipComponent, SVGRenderer])

type Option = ComposeOption<
  | BarSeriesOption
  | LineSeriesOption
  | GridComponentOption
  | LegendComponentOption
  | TooltipComponentOption
>

/**
 * A chart of one report table (KI-4). The table stays beside it as the exact, accessible view;
 * the chart only makes the trend visible. One series → bars, several → lines.
 */
const props = defineProps<{ chart: TableChart }>()
const { locale } = useI18n()
const vars = useThemeVars()
const dark = usePreferredDark()

// The validated reference palette (dataviz skill), in fixed order; dark steps for the dark surface.
const PALETTE = {
  light: ['#2a78d6', '#eb6834', '#1baf7a', '#eda100', '#e87ba4', '#008300', '#4a3aa7', '#e34948'],
  dark: ['#3987e5', '#d95926', '#199e70', '#c98500', '#d55181', '#008300', '#9085e9', '#e66767'],
}

const el = ref<HTMLDivElement | null>(null)
let instance: ECharts | null = null

const format = (value: number | null | undefined) => {
  if (value === null || value === undefined) return '—'
  const { prefix, suffix } = props.chart.unit
  const number = new Intl.NumberFormat(locale.value, { maximumFractionDigits: 2 }).format(value)
  return `${prefix}${number}${suffix && suffix !== '%' ? ' ' : ''}${suffix}`
}

const option = computed<Option>(() => {
  const { periods, series, visible } = props.chart
  const colors = dark.value ? PALETTE.dark : PALETTE.light
  const muted = vars.value.textColor3
  const text = vars.value.textColor2
  const surface = vars.value.cardColor
  const bars = series.length === 1
  return {
    color: colors,
    animation: false,
    textStyle: { fontFamily: vars.value.fontFamily },
    grid: { left: 8, right: 16, top: series.length > 1 ? 36 : 12, bottom: 8, containLabel: true },
    legend:
      series.length > 1
        ? {
            type: 'scroll',
            top: 0,
            left: 0,
            icon: 'roundRect',
            pageTextStyle: { color: muted },
            pageIconColor: text,
            pageIconInactiveColor: vars.value.borderColor,
            itemWidth: 12,
            itemHeight: 4,
            textStyle: { color: text, fontSize: 12 },
            // Switched-off series must look off in both themes (ECharts' default is a light grey).
            inactiveColor: vars.value.textColorDisabled,
            inactiveBorderColor: vars.value.textColorDisabled,
            selected: Object.fromEntries(series.map((s, i) => [s.name, i < visible])),
          }
        : undefined,
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: bars ? 'shadow' : 'line', lineStyle: { color: muted } },
      backgroundColor: vars.value.popoverColor,
      borderColor: vars.value.borderColor,
      textStyle: { color: vars.value.textColor1, fontSize: 12 },
      valueFormatter: (value) => format(value as number | null),
    },
    xAxis: {
      type: 'category',
      data: periods,
      boundaryGap: bars,
      axisLine: { lineStyle: { color: vars.value.borderColor } },
      axisTick: { show: false },
      axisLabel: { color: muted, fontSize: 11, hideOverlap: true },
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: vars.value.dividerColor } },
      axisLabel: {
        color: muted,
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
            itemStyle: { borderColor: surface, borderWidth: 2 },
            emphasis: { focus: 'series' },
          } satisfies LineSeriesOption),
    ),
  }
})

onMounted(() => {
  instance = init(el.value!, undefined, { renderer: 'svg' })
  instance.setOption(option.value)
})
watch(option, (next) => instance?.setOption(next, { notMerge: true }))
useResizeObserver(el, () => instance?.resize())
onBeforeUnmount(() => instance?.dispose())

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
