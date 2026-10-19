import { describe, expect, it } from 'vitest'
import type { Role, User } from '@/shared/api/types'
import { disableBlocked, roleChangeBlocked } from './guardRails'

const roles: Role[] = [
  { id: 'r-admin', name: 'ADMIN', builtIn: true, description: null, permissions: [] },
  { id: 'r-viewer', name: 'VIEWER', builtIn: true, description: null, permissions: [] },
  { id: 'r-custom', name: 'ADMIN', builtIn: false, description: null, permissions: [] },
]

function user(id: string, roleIds: string[], enabled = true): User {
  return {
    id,
    username: id,
    roles: roleIds.map((roleId) => ({ id: roleId, name: roleId })),
    enabled,
    mustChangePassword: false,
    locked: false,
    lockedUntil: null,
    lastLoginAt: null,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    version: 0,
  }
}

describe('disableBlocked', () => {
  it('blocks disabling the caller', () => {
    const me = user('me', ['r-viewer'])
    expect(disableBlocked(me, 'me', [me], roles)).toBe('cannot_disable_self')
  })

  it('blocks disabling the only enabled ADMIN', () => {
    const admin = user('a', ['r-admin'])
    const users = [user('me', ['r-viewer']), admin]
    expect(disableBlocked(admin, 'me', users, roles)).toBe('last_admin')
  })

  it('lets a second enabled ADMIN lift the block', () => {
    const admin = user('a', ['r-admin'])
    const users = [admin, user('b', ['r-admin'])]
    expect(disableBlocked(admin, 'me', users, roles)).toBeNull()
  })

  it('does not count a disabled second ADMIN', () => {
    const admin = user('a', ['r-admin'])
    const users = [admin, user('b', ['r-admin'], false)]
    expect(disableBlocked(admin, 'me', users, roles)).toBe('last_admin')
  })

  it('does not block a user without ADMIN', () => {
    const viewer = user('v', ['r-viewer'])
    expect(disableBlocked(viewer, 'me', [viewer, user('a', ['r-admin'])], roles)).toBeNull()
  })
})

describe('roleChangeBlocked', () => {
  it('blocks the caller removing their own ADMIN', () => {
    const me = user('me', ['r-admin'])
    const users = [me, user('b', ['r-admin'])]
    expect(roleChangeBlocked(me, ['r-viewer'], 'me', users, roles)).toBe('cannot_remove_own_admin')
  })

  it('blocks removing ADMIN from the only enabled ADMIN', () => {
    const admin = user('a', ['r-admin'])
    const users = [user('me', ['r-viewer']), admin]
    expect(roleChangeBlocked(admin, ['r-viewer'], 'me', users, roles)).toBe('last_admin')
  })

  it('allows removing ADMIN while another enabled ADMIN remains', () => {
    const admin = user('a', ['r-admin'])
    const users = [admin, user('b', ['r-admin'])]
    expect(roleChangeBlocked(admin, ['r-viewer'], 'me', users, roles)).toBeNull()
  })

  it('allows a change that keeps ADMIN', () => {
    const me = user('me', ['r-admin'])
    expect(roleChangeBlocked(me, ['r-admin', 'r-viewer'], 'me', [me], roles)).toBeNull()
  })

  it('does not block a role change without ADMIN involved', () => {
    const viewer = user('v', ['r-viewer'])
    expect(roleChangeBlocked(viewer, ['r-custom'], 'me', [viewer], roles)).toBeNull()
  })
})
