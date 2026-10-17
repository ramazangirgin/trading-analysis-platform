import { describe, expect, it } from 'vitest'
import { permissionGateRedirect } from './permissions'

const none = () => false
const only =
  (...keys: string[]) =>
  (permission: string) =>
    keys.includes(permission)

describe('permissionGateRedirect', () => {
  it('lets a route without meta.permission through', () => {
    expect(permissionGateRedirect(none, { name: 'reports' })).toBeNull()
    expect(permissionGateRedirect(none, { name: 'reports', meta: {} })).toBeNull()
  })

  it('redirects to the dashboard without the permission', () => {
    expect(
      permissionGateRedirect(none, { name: 'settings', meta: { permission: 'settings:read' } }),
    ).toEqual({ name: 'dashboard' })
  })

  it('lets a user with the permission through', () => {
    expect(
      permissionGateRedirect(only('settings:read'), {
        name: 'settings',
        meta: { permission: 'settings:read' },
      }),
    ).toBeNull()
  })

  it('accepts any permission of a list', () => {
    const to = { name: 'x', meta: { permission: ['analysis:run', 'settings:read'] } }
    expect(permissionGateRedirect(only('settings:read'), to)).toBeNull()
    expect(permissionGateRedirect(only('preset:read'), to)).toEqual({ name: 'dashboard' })
  })

  it('never redirects the dashboard', () => {
    expect(
      permissionGateRedirect(none, { name: 'dashboard', meta: { permission: 'analysis:run' } }),
    ).toBeNull()
  })
})
