import { request } from '@/shared/api/client'
import type { AnalysisReport, ImportResult, PriceHistory } from '@/shared/api/types'

export const reportsApi = {
  getReport(id: string): Promise<AnalysisReport> {
    return request(`/api/analyses/${encodeURIComponent(id)}/report`)
  },
  getPrices(id: string): Promise<PriceHistory> {
    return request(`/api/analyses/${encodeURIComponent(id)}/prices`)
  },
  rescanReports(): Promise<ImportResult> {
    return request('/api/reports/rescan', { method: 'POST' })
  },
}
