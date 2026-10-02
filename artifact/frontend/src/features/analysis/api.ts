import { request } from '@/shared/api/client'
import type { Analysis, AnalysisStatus, StartAnalysisRequest } from '@/shared/api/types'

export const analysisApi = {
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
  getLogs(id: string, tail = 1000): Promise<{ lines: string[] }> {
    return request(`/api/analyses/${encodeURIComponent(id)}/logs?tail=${tail}`)
  },
  eventsUrl(id: string): string {
    return `/api/analyses/${encodeURIComponent(id)}/events`
  },
}
