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

export const ACTIVE_STATUSES: readonly AnalysisStatus[] = ['QUEUED', 'RUNNING']

/** An error the backend answered with; the UI shows a translation of `errorCode`. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly errorCode: string,
    readonly params: Record<string, unknown>,
    message: string,
  ) {
    super(message)
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response
  try {
    response = await fetch(path, {
      ...init,
      headers: {
        Accept: 'application/json',
        ...(init?.body ? { 'Content-Type': 'application/json' } : {}),
      },
    })
  } catch (e) {
    throw new ApiError(0, 'network_error', {}, String(e))
  }
  if (!response.ok) {
    const body = await response.json().catch(() => ({}))
    throw new ApiError(
      response.status,
      typeof body.errorCode === 'string' ? body.errorCode : 'unexpected_error',
      body.params ?? {},
      body.message ?? response.statusText,
    )
  }
  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}

export const api = {
  listAnalyses(filter: { status?: AnalysisStatus; ticker?: string } = {}): Promise<Analysis[]> {
    const query = new URLSearchParams()
    if (filter.status) query.set('status', filter.status)
    if (filter.ticker) query.set('ticker', filter.ticker)
    const suffix = query.size ? `?${query}` : ''
    return request(`/api/analyses${suffix}`)
  },
  getAnalysis(id: string): Promise<Analysis> {
    return request(`/api/analyses/${encodeURIComponent(id)}`)
  },
  startAnalysis(body: StartAnalysisRequest): Promise<Analysis> {
    return request('/api/analyses', { method: 'POST', body: JSON.stringify(body) })
  },
  stopAnalysis(id: string): Promise<Analysis> {
    return request(`/api/analyses/${encodeURIComponent(id)}/stop`, { method: 'POST' })
  },
  rerunAnalysis(id: string): Promise<Analysis> {
    return request(`/api/analyses/${encodeURIComponent(id)}/rerun`, { method: 'POST' })
  },
  getReport(id: string): Promise<AnalysisReport> {
    return request(`/api/analyses/${encodeURIComponent(id)}/report`)
  },
  getPrices(id: string): Promise<PriceHistory> {
    return request(`/api/analyses/${encodeURIComponent(id)}/prices`)
  },
  rescanReports(): Promise<ImportResult> {
    return request('/api/reports/rescan', { method: 'POST' })
  },
  getLogs(id: string, tail = 1000): Promise<{ lines: string[] }> {
    return request(`/api/analyses/${encodeURIComponent(id)}/logs?tail=${tail}`)
  },
  getHealth(): Promise<SystemHealth> {
    return request('/api/health')
  },
  listSecrets(): Promise<SecretStatus[]> {
    return request('/api/secrets')
  },
  setSecret(name: string, value: string): Promise<SecretStatus> {
    return request(`/api/secrets/${encodeURIComponent(name)}`, {
      method: 'PUT',
      body: JSON.stringify({ value }),
    })
  },
  removeSecret(name: string): Promise<void> {
    return request(`/api/secrets/${encodeURIComponent(name)}`, { method: 'DELETE' })
  },
  listPresets(): Promise<Preset[]> {
    return request('/api/presets')
  },
  createPreset(name: string, values: Record<string, unknown>): Promise<Preset> {
    return request('/api/presets', { method: 'POST', body: JSON.stringify({ name, values }) })
  },
  deletePreset(id: string): Promise<void> {
    return request(`/api/presets/${encodeURIComponent(id)}`, { method: 'DELETE' })
  },
  getCatalog(): Promise<Catalog> {
    return request('/api/catalog')
  },
  eventsUrl(id: string): string {
    return `/api/analyses/${encodeURIComponent(id)}/events`
  },
}
