import { expect, request, type FullConfig } from '@playwright/test'
import { csrfHeaders, E2E_PASSWORD, E2E_USER } from './support'
import { warmUpApi } from './warm-up'

/**
 * Logs the seeded user in over the API, warms the backend up with that session, and saves the
 * session (cookies with the CSRF token) as the storage state every test starts with.
 */
export default async function globalSetup(config: FullConfig) {
  const { baseURL, storageState } = config.projects[0]!.use
  const api = await request.newContext({ baseURL })
  try {
    // The CSRF cookie comes with any answer of /api/auth/me, a 401 included.
    await api.get('/api/auth/me')
    // The seed runs right after the health check that Playwright waits for: retry until it is there.
    await expect(async () => {
      const response = await api.post('/api/auth/login', {
        data: { username: E2E_USER, password: E2E_PASSWORD },
        headers: await csrfHeaders(api),
      })
      expect(response.ok(), await response.text()).toBe(true)
    }).toPass({ timeout: 30_000 })
    await warmUpApi(api)
    await api.storageState({ path: storageState as string })
  } finally {
    await api.dispose()
  }
}
