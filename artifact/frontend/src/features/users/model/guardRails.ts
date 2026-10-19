import type { Role, User } from '@/shared/api/types'

export type GuardRail = 'cannot_disable_self' | 'cannot_remove_own_admin' | 'last_admin'

function adminRoleId(roles: Role[]): string | undefined {
  return roles.find((role) => role.builtIn && role.name === 'ADMIN')?.id
}

function isLastEnabledAdmin(target: User, users: User[], adminId: string | undefined): boolean {
  if (!adminId || !target.enabled) return false
  const holdsAdmin = (user: User) => user.roles.some((role) => role.id === adminId)
  if (!holdsAdmin(target)) return false
  return !users.some((user) => user.id !== target.id && user.enabled && holdsAdmin(user))
}

/** What the backend would answer to disabling the target, or null when nothing stops it. */
export function disableBlocked(
  target: User,
  currentUserId: string,
  users: User[],
  roles: Role[],
): GuardRail | null {
  if (target.id === currentUserId) return 'cannot_disable_self'
  if (isLastEnabledAdmin(target, users, adminRoleId(roles))) return 'last_admin'
  return null
}

/** What the backend would answer to giving the target exactly these roles, or null. */
export function roleChangeBlocked(
  target: User,
  newRoleIds: string[],
  currentUserId: string,
  users: User[],
  roles: Role[],
): GuardRail | null {
  const adminId = adminRoleId(roles)
  if (!adminId || newRoleIds.includes(adminId)) return null
  if (target.id === currentUserId && target.roles.some((role) => role.id === adminId)) {
    return 'cannot_remove_own_admin'
  }
  if (isLastEnabledAdmin(target, users, adminId)) return 'last_admin'
  return null
}
