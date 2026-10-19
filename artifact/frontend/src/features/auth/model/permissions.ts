import type { RouteMeta } from 'vue-router'
import type { Permission } from '@/shared/api/types'

/**
 * Friendly names for the permissions the UI asks about. The set of keys is the generated
 * `Permission` type (from the backend's enum through OpenAPI), so a removed key fails the type-check.
 */
export const PERMISSIONS = {
  analysisRun: 'analysis:run',
  analysisRead: 'analysis:read',
  analysisReadAll: 'analysis:read:all',
  presetRead: 'preset:read',
  presetManage: 'preset:manage',
  settingsRead: 'settings:read',
  settingsKeysWrite: 'settings:keys:write',
  userRead: 'user:read',
  userManage: 'user:manage',
  roleManage: 'role:manage',
} as const satisfies Record<string, Permission>

declare module 'vue-router' {
  interface RouteMeta {
    permission?: Permission | Permission[]
  }
}

export interface PermissionTarget {
  name?: string | symbol | null
  meta?: RouteMeta
}

/**
 * Where a user is sent instead of the target: the dashboard when the target route declares
 * `meta.permission` (one key, or a list of which any is enough) and the user holds none of them,
 * else nowhere (`null`). The dashboard itself is open to every logged-in user.
 */
export function permissionGateRedirect(
  can: (permission: Permission) => boolean,
  to: PermissionTarget,
) {
  if (to.name === 'dashboard') return null
  const required = to.meta?.permission
  if (required === undefined || required === null) return null
  const anyOf = Array.isArray(required) ? required : [required]
  if (anyOf.some((permission) => can(permission))) return null
  return { name: 'dashboard' }
}
