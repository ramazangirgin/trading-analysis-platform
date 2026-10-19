import { request } from '@/shared/api/client'
import type { CreateRoleRequest, Role, UpdateRoleRequest } from '@/shared/api/types'

export const rolesApi = {
  list(): Promise<Role[]> {
    return request('/api/roles')
  },
  create(body: CreateRoleRequest): Promise<Role> {
    return request('/api/roles', { method: 'POST', body: JSON.stringify(body) })
  },
  update(id: string, body: UpdateRoleRequest): Promise<Role> {
    return request(`/api/roles/${encodeURIComponent(id)}`, {
      method: 'PATCH',
      body: JSON.stringify(body),
    })
  },
  remove(id: string): Promise<void> {
    return request(`/api/roles/${encodeURIComponent(id)}`, { method: 'DELETE' })
  },
}
