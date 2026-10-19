import type { Role } from '@/shared/api/types'

export type RoleGuard = 'built_in_role' | 'role_in_use'

const ADMIN_ID = 'ADMIN'

/** Built-in roles keep their name and description. */
export function renameBlocked(role: Role): boolean {
  return role.builtIn
}

/** The permissions of ADMIN are fixed, so an admin cannot lock themselves out. */
export function permissionsLocked(role: Role): boolean {
  return role.builtIn && role.id === ADMIN_ID
}

/** What the backend would answer to deleting the role, or null when nothing stops it. */
export function deleteBlocked(role: Role): RoleGuard | null {
  if (role.builtIn) return 'built_in_role'
  if (role.userCount > 0) return 'role_in_use'
  return null
}
