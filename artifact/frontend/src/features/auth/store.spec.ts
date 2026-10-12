import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { ApiError } from '@/shared/api/error'
import type { CurrentUser } from '@/shared/api/types'

vi.mock('./api', () => ({ authApi: { me: vi.fn(), login: vi.fn(), logout: vi.fn() } }))

import { authApi } from './api'
import { useSessionStore } from './store'

const user: CurrentUser = {
  id: 1,
  username: 'ada',
  roles: ['ADMIN'],
  permissions: ['runs.start', 'settings.edit'],
  mustChangePassword: false,
} as unknown as CurrentUser

describe('useSessionStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.resetAllMocks()
  })

  it('asks the backend once for parallel callers', async () => {
    vi.mocked(authApi.me).mockResolvedValue(user)
    const store = useSessionStore()
    await Promise.all([store.load(), store.load()])
    await store.load()
    expect(authApi.me).toHaveBeenCalledTimes(1)
    expect(store.user).toEqual(user)
    expect(store.username).toBe('ada')
    expect(store.loaded).toBe(true)
  })

  it('has no user after a 401', async () => {
    vi.mocked(authApi.me).mockRejectedValue(new ApiError(401, 'unauthorized', {}, ''))
    const store = useSessionStore()
    await store.load()
    expect(store.user).toBeNull()
    expect(store.loaded).toBe(true)
  })

  it('rethrows other errors and tries again next time', async () => {
    vi.mocked(authApi.me).mockRejectedValueOnce(new ApiError(0, 'network_error', {}, ''))
    vi.mocked(authApi.me).mockResolvedValueOnce(user)
    const store = useSessionStore()
    await expect(store.load()).rejects.toBeInstanceOf(ApiError)
    expect(store.user).toBeNull()
    await store.load()
    expect(store.user).toEqual(user)
  })

  it('answers can() from the permissions', async () => {
    vi.mocked(authApi.login).mockResolvedValue(user)
    const store = useSessionStore()
    expect(store.can('runs.start')).toBe(false)
    await store.login('ada', 'secret')
    expect(authApi.login).toHaveBeenCalledWith('ada', 'secret')
    expect(store.can('runs.start')).toBe(true)
    expect(store.can('users.manage')).toBe(false)
  })

  it('clears the user', async () => {
    vi.mocked(authApi.login).mockResolvedValue(user)
    const store = useSessionStore()
    await store.login('ada', 'secret')
    store.clear()
    expect(store.user).toBeNull()
    expect(store.can('runs.start')).toBe(false)
  })
})
