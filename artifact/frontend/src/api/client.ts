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
  getCatalog(): Promise<Catalog> {
    return request('/api/catalog')
  },
  eventsUrl(id: string): string {
    return `/api/analyses/${encodeURIComponent(id)}/events`
  },
}
