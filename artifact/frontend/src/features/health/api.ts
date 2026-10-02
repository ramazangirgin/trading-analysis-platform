import { request } from '@/shared/api/client'
import type { SystemHealth } from '@/shared/api/types'

export const healthApi = {
  getHealth(): Promise<SystemHealth> {
    return request('/api/health')
  },
}
