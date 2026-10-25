import { request } from '@/shared/api/client'
import type { AnalysisPreview, AnalysisPreviewPage } from '@/shared/api/types'

export const previewApi = {
  list(page: number, size: number): Promise<AnalysisPreviewPage> {
    return request(`/api/public/analyses?page=${page}&size=${size}`)
  },
  get(id: string): Promise<AnalysisPreview> {
    return request(`/api/public/analyses/${encodeURIComponent(id)}`)
  },
}
