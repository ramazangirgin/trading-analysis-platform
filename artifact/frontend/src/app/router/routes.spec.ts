import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import type { CurrentUser } from '@/shared/api/types'
import { createSessionGuard, routes } from './routes'

const fetchMock = vi.fn()

const user: CurrentUser = {
  id: 'u_1',
  username: 'ada',
  roles: ['ANALYST'],
  permissions: ['analysis:read'],
  mustChangePassword: false,
}

function target(name: string, fullPath: string) {
  const route = routes.find((r) => r.name === name)
  return { name, fullPath, meta: route?.meta ?? {} }
}

function visitor() {
  fetchMock.mockImplementation(async () => new Response('{}', { status: 401 }))
}

function loggedIn(current: CurrentUser) {
  fetchMock.mockImplementation(async () => new Response(JSON.stringify(current), { status: 200 }))
}

describe('session guard', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    fetchMock.mockReset()
    vi.stubGlobal('fetch', fetchMock)
    vi.stubGlobal('document', { cookie: '' })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('lets a visitor reach the landing page and a preview', async () => {
    visitor()
    const guard = createSessionGuard()
    expect(await guard(target('dashboard', '/'))).toBe(true)
    expect(await guard(target('preview', '/preview/a1'))).toBe(true)
  })

  it('sends a visitor from a protected page to the login page', async () => {
    visitor()
    const guard = createSessionGuard()
    expect(await guard(target('analyses', '/analyses'))).toEqual({
      name: 'login',
      query: { redirect: '/analyses' },
    })
  })

  it('lets a logged-in user reach the dashboard', async () => {
    loggedIn(user)
    expect(await createSessionGuard()(target('dashboard', '/'))).toBe(true)
  })

  it('sends a user who must change the password from / to the password page', async () => {
    loggedIn({ ...user, mustChangePassword: true })
    expect(await createSessionGuard()(target('dashboard', '/'))).toEqual({
      name: 'change-password',
      query: { redirect: '/' },
    })
  })
})
