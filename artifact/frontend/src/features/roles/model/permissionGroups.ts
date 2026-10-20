import type { Permission } from '@/shared/api/types'

export const ALL_PERMISSIONS = [
  'analysis:run',
  'analysis:read',
  'analysis:read:all',
  'analysis:delete',
  'analysis:defaults:manage',
  'analysis:override-models',
  'preset:read',
  'preset:manage',
  'preset:read:all',
  'settings:read',
  'settings:keys:write',
  'user:read',
  'user:manage',
  'role:manage',
  'audit:read',
] as const satisfies readonly Permission[]

// Fails the type-check when a generated Permission value is missing from the list above.
type Listed = (typeof ALL_PERMISSIONS)[number]
export type AllPermissionsListed = [Exclude<Permission, Listed>] extends [never] ? true : never

export type PermissionArea = 'analysis' | 'preset' | 'settings' | 'user' | 'audit'

export interface PermissionGroup {
  area: PermissionArea
  permissions: Permission[]
}

const AREAS: PermissionArea[] = ['analysis', 'preset', 'settings', 'user', 'audit']

function areaOf(permission: Permission): PermissionArea {
  const segment = permission.split(':')[0]
  return segment === 'role' ? 'user' : (segment as PermissionArea)
}

export function permissionGroups(): PermissionGroup[] {
  return AREAS.map((area) => ({
    area,
    permissions: ALL_PERMISSIONS.filter((permission) => areaOf(permission) === area),
  }))
}
