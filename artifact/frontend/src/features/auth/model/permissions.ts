/** The permission keys the UI asks about (the catalogue lives in the backend's `Permission`). */
export const PERMISSIONS = {
  analysisRun: 'analysis:run',
  analysisRead: 'analysis:read',
  presetRead: 'preset:read',
  presetManage: 'preset:manage',
  settingsRead: 'settings:read',
  settingsKeysWrite: 'settings:keys:write',
} as const

export interface PermissionTarget {
  name?: string | symbol | null
  meta?: object
}

/**
 * Where a user is sent instead of the target: the dashboard when the target route declares
 * `meta.permission` (one key, or a list of which any is enough) and the user holds none of them,
 * else nowhere (`null`). The dashboard itself is open to every logged-in user.
 */
export function permissionGateRedirect(can: (permission: string) => boolean, to: PermissionTarget) {
  if (to.name === 'dashboard') return null
  const required = (to.meta as { permission?: unknown } | undefined)?.permission
  if (required === undefined || required === null) return null
  const anyOf = Array.isArray(required) ? required : [required]
  if (anyOf.some((permission) => typeof permission === 'string' && can(permission))) return null
  return { name: 'dashboard' }
}
