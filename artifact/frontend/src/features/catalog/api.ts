import { request } from '@/shared/api/client'
import type { Catalog } from '@/shared/api/types'

export const catalogApi = {
  getCatalog(): Promise<Catalog> {
    return request('/api/catalog')
  },
}
