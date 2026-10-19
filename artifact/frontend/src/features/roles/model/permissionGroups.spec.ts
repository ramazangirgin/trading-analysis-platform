import { describe, expect, it } from 'vitest'
import { ALL_PERMISSIONS, permissionGroups } from './permissionGroups'

describe('permissionGroups', () => {
  it('puts every permission in exactly one group', () => {
    const grouped = permissionGroups().flatMap((group) => group.permissions)
    expect([...grouped].sort()).toEqual([...ALL_PERMISSIONS].sort())
  })

  it('orders the groups analysis, preset, settings, user, audit', () => {
    expect(permissionGroups().map((group) => group.area)).toEqual([
      'analysis',
      'preset',
      'settings',
      'user',
      'audit',
    ])
  })

  it('keeps role:manage in the user group', () => {
    const user = permissionGroups().find((group) => group.area === 'user')
    expect(user?.permissions).toContain('role:manage')
  })
})
