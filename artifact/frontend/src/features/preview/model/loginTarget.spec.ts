import { describe, expect, it } from 'vitest'
import { safeRedirect } from '@/features/auth'
import { loginLocation } from './loginTarget'

describe('loginLocation', () => {
  it('points to the login route with the full analysis as redirect', () => {
    expect(loginLocation('abc-1')).toEqual({
      name: 'login',
      query: { redirect: '/analyses/abc-1' },
    })
  })

  it('gives a redirect that safeRedirect accepts', () => {
    const location = loginLocation('abc-1') as { query: { redirect: string } }
    expect(safeRedirect(location.query.redirect)).toBe('/analyses/abc-1')
  })
})
