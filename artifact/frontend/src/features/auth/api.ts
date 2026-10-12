import { onUnauthorized, request } from '@/shared/api/client'
import type { CurrentUser } from '@/shared/api/types'

/** Registers the one handler called when a request finds the session ended (any 401 but login's). */
export const onSessionEnded = onUnauthorized

// A 401 of `me` and `login` is their answer (no session, wrong credentials), not an ended session.
export const authApi = {
  me(): Promise<CurrentUser> {
    return request('/api/auth/me', undefined, { skipUnauthorized: true })
  },
  login(username: string, password: string): Promise<CurrentUser> {
    return request(
      '/api/auth/login',
      { method: 'POST', body: JSON.stringify({ username, password }) },
      { skipUnauthorized: true },
    )
  },
  logout(): Promise<void> {
    return request('/api/auth/logout', { method: 'POST' })
  },
}
