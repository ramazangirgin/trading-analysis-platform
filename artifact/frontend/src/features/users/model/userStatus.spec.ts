import { describe, expect, it } from 'vitest'
import { userStatus } from './userStatus'

describe('userStatus', () => {
  it('is active for an enabled, unlocked user', () => {
    expect(userStatus({ enabled: true, locked: false })).toBe('active')
  })

  it('is locked for an enabled, locked user', () => {
    expect(userStatus({ enabled: true, locked: true })).toBe('locked')
  })

  it('is disabled for a disabled user', () => {
    expect(userStatus({ enabled: false, locked: false })).toBe('disabled')
  })

  it('lets disabled win over locked', () => {
    expect(userStatus({ enabled: false, locked: true })).toBe('disabled')
  })
})
