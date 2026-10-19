import { request } from '@/shared/api/client'
import type {
  CreatedUser,
  CreateUserRequest,
  OneTimePassword,
  Role,
  UpdateUserRequest,
  User,
} from '@/shared/api/types'

export const usersApi = {
  list(): Promise<User[]> {
    return request('/api/users')
  },
  create(body: CreateUserRequest): Promise<CreatedUser> {
    return request('/api/users', { method: 'POST', body: JSON.stringify(body) })
  },
  update(id: string, body: UpdateUserRequest): Promise<User> {
    return request(`/api/users/${encodeURIComponent(id)}`, {
      method: 'PATCH',
      body: JSON.stringify(body),
    })
  },
  resetPassword(id: string, password?: string): Promise<OneTimePassword> {
    return request(`/api/users/${encodeURIComponent(id)}/password-reset`, {
      method: 'POST',
      body: JSON.stringify({ password: password?.trim() ? password : undefined }),
    })
  },
  unlock(id: string): Promise<void> {
    return request(`/api/users/${encodeURIComponent(id)}/unlock`, { method: 'POST' })
  },
  listRoles(): Promise<Role[]> {
    return request('/api/roles')
  },
}
