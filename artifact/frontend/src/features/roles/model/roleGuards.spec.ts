import { describe, expect, it } from 'vitest'
import type { Role } from '@/shared/api/types'
import { deleteBlocked, permissionsLocked, renameBlocked } from './roleGuards'

function role(overrides: Partial<Role>): Role {
  return {
    id: 'r1',
    name: 'R1',
    builtIn: false,
    description: null,
    permissions: [],
    userCount: 0,
    version: 0,
    ...overrides,
  }
}

const admin = role({ id: 'ADMIN', name: 'ADMIN', builtIn: true, userCount: 1 })
const analyst = role({ id: 'ANALYST', name: 'ANALYST', builtIn: true })

describe('roleGuards', () => {
  it('blocks renaming and deleting a built-in role', () => {
    expect(renameBlocked(analyst)).toBe(true)
    expect(deleteBlocked(analyst)).toBe('built_in_role')
    expect(renameBlocked(role({}))).toBe(false)
  })

  it('locks the permissions of ADMIN only', () => {
    expect(permissionsLocked(admin)).toBe(true)
    expect(permissionsLocked(analyst)).toBe(false)
    expect(permissionsLocked(role({ id: 'ADMIN' }))).toBe(false)
  })

  it('blocks deleting a custom role that users hold', () => {
    expect(deleteBlocked(role({ userCount: 2 }))).toBe('role_in_use')
    expect(deleteBlocked(role({ userCount: 0 }))).toBeNull()
  })
})
