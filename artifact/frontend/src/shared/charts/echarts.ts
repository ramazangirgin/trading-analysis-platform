import { init, use, type ComposeOption, type ECharts } from 'echarts/core'
import { BarChart, LineChart, type BarSeriesOption, type LineSeriesOption } from 'echarts/charts'
import {
  AxisPointerComponent,
  GridComponent,
  LegendComponent,
  MarkLineComponent,
  TooltipComponent,
  type GridComponentOption,
  type LegendComponentOption,
  type MarkLineComponentOption,
  type TooltipComponentOption,
} from 'echarts/components'
import { SVGRenderer } from 'echarts/renderers'

// Only what the report charts use, so the lazily loaded chunk stays as small as ECharts allows.
use([
  BarChart,
  LineChart,
  AxisPointerComponent,
  GridComponent,
  LegendComponent,
  MarkLineComponent,
  TooltipComponent,
  SVGRenderer,
])

export type ChartOption = ComposeOption<
  | BarSeriesOption
  | LineSeriesOption
  | GridComponentOption
  | LegendComponentOption
  | MarkLineComponentOption
  | TooltipComponentOption
>
export type { BarSeriesOption, ECharts, LineSeriesOption }

export const createChart = (el: HTMLElement): ECharts => init(el, undefined, { renderer: 'svg' })
