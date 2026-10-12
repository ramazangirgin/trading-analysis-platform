/** The routes a user who must change the password may still see. */
const OPEN_ROUTES = new Set(['login', 'change-password'])

export interface GateTarget {
  name?: string | symbol | null
  fullPath: string
}

/**
 * Where a user is sent instead of the target: the change-password page (keeping the target in
 * `redirect`) while the account must change its password, else nowhere (`null`).
 */
export function passwordGateRedirect(mustChangePassword: boolean, target: GateTarget) {
  if (!mustChangePassword) return null
  if (typeof target.name === 'string' && OPEN_ROUTES.has(target.name)) return null
  return { name: 'change-password', query: { redirect: target.fullPath } }
}
