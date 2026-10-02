// The API's data shapes, derived from the generated OpenAPI schema (schema.d.ts).
import type { components } from './schema'

type Schemas = components['schemas']

/** springdoc marks every field optional; the backend always sends them, some as null. */
type Present<T, Nullable extends keyof T = never> = {
  [K in keyof T]-?: K extends Nullable ? Exclude<T[K], undefined> | null : Exclude<T[K], undefined>
}

export type AnalysisStatus = NonNullable<Schemas['AnalysisDto']['status']>
export type Rating = NonNullable<Schemas['AnalysisDto']['rating']>
export type Analyst = NonNullable<Schemas['StartAnalysisRequest']['analysts']>[number]
export type AnalysisSpec = Present<Schemas['AnalysisSpecDto']>
export type RunStats = Present<Schemas['RunStatsDto'], 'costUsd'>
export type Analysis = Omit<
  Present<
    Schemas['AnalysisDto'],
    'rating' | 'decision' | 'startedAt' | 'endedAt' | 'errorCode' | 'errorMessage'
  >,
  'spec' | 'stats'
> & { spec: AnalysisSpec; stats: RunStats }
export type StartAnalysisRequest = Schemas['StartAnalysisRequest']
export type Provider = Omit<
  Present<Schemas['ProviderDto'], 'apiKeyEnv'>,
  'quickModels' | 'deepModels'
> & {
  quickModels: ModelOption[]
  deepModels: ModelOption[]
}
export type ModelOption = Present<Schemas['ModelOptionDto']>
export type Catalog = Omit<
  Present<Schemas['CatalogDto']>,
  'providers' | 'defaults' | 'analysts'
> & {
  providers: Provider[]
  defaults: Present<Schemas['ModelDefaultsDto']>
  analysts: Present<Schemas['AnalystOptionDto']>[]
}

export type AnalysisReport = Omit<
  Present<Schemas['AnalysisReportDto'], 'rating'>,
  'sections' | 'debates'
> & {
  sections: Record<string, string>
  debates: Record<string, string>
}
export type PricePoint = Present<Schemas['PricePointDto']>
export type PriceHistory = Omit<Present<Schemas['PriceHistoryDto']>, 'points'> & {
  points: PricePoint[]
}
export type ImportResult = Present<Schemas['ImportResultDto']>
export type HealthCheck = Omit<Present<Schemas['HealthCheckDto']>, 'params'> & {
  status: 'UP' | 'WARN' | 'DOWN'
  params: Record<string, unknown>
}
export type SystemHealth = { overall: 'UP' | 'WARN' | 'DOWN'; checks: HealthCheck[] }
export type SecretStatus = Present<Schemas['SecretStatusDto']>
export type Preset = Omit<Present<Schemas['PresetDto']>, 'values'> & {
  values: Record<string, unknown>
}

/** One runner event from the SSE stream (docs/event-protocol.md, section 4). */
export interface RunEvent {
  seq: number
  timestamp: string
  type: string
  payload: Record<string, unknown>
}
