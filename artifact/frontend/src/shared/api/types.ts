// The API's data shapes, derived from the generated OpenAPI schema (schema.d.ts).
import type { components, operations } from './schema'

type Schemas = components['schemas']

/** springdoc marks every field optional; the backend always sends them, some as null. */
type Present<T, Nullable extends keyof T = never> = {
  [K in keyof T]-?: K extends Nullable ? Exclude<T[K], undefined> | null : Exclude<T[K], undefined>
}

export type AnalysisStatus = NonNullable<Schemas['AnalysisDto']['status']>
export type Visibility = NonNullable<Schemas['AnalysisDto']['visibility']> | 'PARTIAL'
export type AnalysisScope = NonNullable<
  NonNullable<operations['listAnalyses']['parameters']['query']>['scope']
>
export type Rating = NonNullable<Schemas['AnalysisDto']['rating']>
export type Analyst = NonNullable<Schemas['StartAnalysisRequest']['analysts']>[number]
export type AnalysisSpec = Present<Schemas['AnalysisSpecDto']>
export type RunStats = Present<Schemas['RunStatsDto'], 'costUsd'>
export type Analysis = Omit<
  Present<
    Schemas['AnalysisDto'],
    | 'rating'
    | 'decision'
    | 'startedAt'
    | 'endedAt'
    | 'errorCode'
    | 'errorMessage'
    | 'visibilityChangedAt'
  >,
  'spec' | 'stats' | 'visibility'
> & {
  spec: AnalysisSpec
  stats: RunStats
  visibility: Visibility
  allowedVisibilities: Visibility[]
}

// The public preview endpoints (`/api/public/analyses`), written by hand until the generated
// schema carries them.
export type AnalysisPreviewSummary = {
  id: string
  ticker: string
  analysisDate: string
  rating: Rating
  excerpt: string
}
export type AnalysisPreviewPage = { items: AnalysisPreviewSummary[]; hasMore: boolean }
export type AnalysisPreview = {
  id: string
  ticker: string
  analysisDate: string
  rating: Rating
  sections: Record<string, string>
  lockedSections: string[]
}
export type StartAnalysisRequest = Schemas['StartAnalysisRequest']
export type ChangeVisibilityRequest = Omit<Schemas['ChangeVisibilityRequest'], 'visibility'> & {
  visibility: Visibility
}
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
export type Permission = NonNullable<Schemas['CurrentUserDto']['permissions']>[number]
export type CurrentUser = Present<Schemas['CurrentUserDto']>
export type ChangePasswordRequest = Present<Schemas['ChangePasswordRequest']>
export type SecretStatus = Present<Schemas['SecretStatusDto']>
export type UserRole = Present<Schemas['UserRoleDto']>
export type User = Omit<Present<Schemas['UserDto'], 'lockedUntil' | 'lastLoginAt'>, 'roles'> & {
  roles: UserRole[]
}
export type Role = Present<Schemas['RoleDto'], 'description'>
export type CreateRoleRequest = Schemas['CreateRoleRequest']
export type UpdateRoleRequest = Schemas['UpdateRoleRequest']
export type CreateUserRequest = Schemas['CreateUserRequest']
export type UpdateUserRequest = Schemas['UpdateUserRequest']
export type CreatedUser = { user: User; oneTimePassword: string | null }
export type OneTimePassword = { oneTimePassword: string | null }
export type AnalysisDefaults = Present<Schemas['AnalysisDefaultsDto'], 'version'>
export type SaveAnalysisDefaultsRequest = Schemas['SaveAnalysisDefaultsRequest']
export type PresetValues = Schemas['PresetValuesDto']
export type Preset = Present<Schemas['PresetDto']>

/** One runner event from the SSE stream (docs/event-protocol.md, section 4). */
export interface RunEvent {
  seq: number
  timestamp: string
  type: string
  payload: Record<string, unknown>
}
