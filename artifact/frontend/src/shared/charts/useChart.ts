import { computed, onBeforeUnmount, watch, type Ref } from 'vue'
import { useThemeVars } from 'naive-ui'
import { usePreferredDark, useResizeObserver } from '@vueuse/core'
import { type ChartOption, createChart, type ECharts } from './echarts'

// The validated reference palette (dataviz skill), in fixed order; dark steps for the dark surface.
const PALETTE = {
  light: ['#2a78d6', '#eb6834', '#1baf7a', '#eda100', '#e87ba4', '#008300', '#4a3aa7', '#e34948'],
  dark: ['#3987e5', '#d95926', '#199e70', '#c98500', '#d55181', '#008300', '#9085e9', '#e66767'],
}

/** Colours and recessive chrome for a chart, following Naive UI's theme and the OS dark mode. */
export function useChartTheme() {
  const vars = useThemeVars()
  const dark = usePreferredDark()
  return computed(() => ({
    series: dark.value ? PALETTE.dark : PALETTE.light,
    text: vars.value.textColor2,
    muted: vars.value.textColor3,
    disabled: vars.value.textColorDisabled,
    border: vars.value.borderColor,
    divider: vars.value.dividerColor,
    surface: vars.value.cardColor,
    popover: vars.value.popoverColor,
    strong: vars.value.textColor1,
    font: vars.value.fontFamily,
  }))
}

/**
 * Keeps an ECharts instance on `el` at the latest option and the element's size. The instance is
 * created once the element exists, so a chart can sit behind a `v-if` until its data arrives
 * (created on a hidden element, ECharts would lay out at zero size).
 */
export function useChart(el: Ref<HTMLElement | null>, option: Ref<ChartOption>) {
  let chart: ECharts | null = null
  watch(
    [el, option],
    ([element, next]) => {
      if (!element) return
      if (!chart) {
        chart = createChart(element)
        chart.setOption(next)
      } else {
        chart.setOption(next, { notMerge: true })
      }
    },
    { immediate: true, flush: 'post' },
  )
  useResizeObserver(el, () => chart?.resize())
  onBeforeUnmount(() => chart?.dispose())
}
