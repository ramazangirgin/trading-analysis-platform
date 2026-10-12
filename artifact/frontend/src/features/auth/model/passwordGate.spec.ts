import { describe, expect, it } from 'vitest'
import { passwordGateRedirect } from './passwordGate'

describe('passwordGateRedirect', () => {
  it('sends a flagged user to the change-password page, keeping the target', () => {
    expect(passwordGateRedirect(true, { name: 'reports', fullPath: '/reports?x=1' })).toEqual({
      name: 'change-password',
      query: { redirect: '/reports?x=1' },
    })
  })

  it('also catches a route without a name', () => {
    expect(passwordGateRedirect(true, { fullPath: '/x' })).toEqual({
      name: 'change-password',
      query: { redirect: '/x' },
    })
  })

  it('lets a flagged user see login and change-password', () => {
    expect(passwordGateRedirect(true, { name: 'login', fullPath: '/login' })).toBeNull()
    expect(
      passwordGateRedirect(true, { name: 'change-password', fullPath: '/password' }),
    ).toBeNull()
  })

  it('never redirects an unflagged user', () => {
    expect(passwordGateRedirect(false, { name: 'reports', fullPath: '/reports' })).toBeNull()
    expect(
      passwordGateRedirect(false, { name: 'change-password', fullPath: '/password' }),
    ).toBeNull()
  })
})
