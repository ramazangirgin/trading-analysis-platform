import { csrfToken } from './csrf'
import { ApiError } from './error'

const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS'])

let unauthorizedHandler: (() => void) | null = null

/**
 * Registers the one handler called on a 401 (an ended session). `shared` knows no router or
 * feature, so the app wires this up.
 */
export function onUnauthorized(handler: () => void): void {
  unauthorizedHandler = handler
}

const PASSWORD_CHANGE_REQUIRED = 'password_change_required'

let passwordChangeHandler: (() => void) | null = null

/**
 * Registers the one handler called on a 403 `password_change_required` (the account must set a new
 * password first). `shared` knows the error code only; the app wires up where to go.
 */
export function onPasswordChangeRequired(handler: () => void): void {
  passwordChangeHandler = handler
}

export interface RequestOptions {
  /** A 401 is an answer of this request (login, current user), not an ended session. */
  skipUnauthorized?: boolean
}

/**
 * The HTTP client. Only the features' `api.ts` modules call it
 * (docs/coding-convention/frontend-folder-structure.md).
 */
export async function request<T>(
  path: string,
  init?: RequestInit,
  options: RequestOptions = {},
): Promise<T> {
  const headers = new Headers(init?.headers)
  headers.set('Accept', 'application/json')
  if (init?.body) headers.set('Content-Type', 'application/json')
  const method = (init?.method ?? 'GET').toUpperCase()
  if (!SAFE_METHODS.has(method)) {
    const token = csrfToken()
    if (token) headers.set('X-XSRF-TOKEN', token)
  }
  let response: Response
  try {
    response = await fetch(path, { ...init, headers })
  } catch (e) {
    throw new ApiError(0, 'network_error', {}, String(e))
  }
  if (!response.ok) {
    if (response.status === 401 && !options.skipUnauthorized) unauthorizedHandler?.()
    const body = await response.json().catch(() => ({}))
    const errorCode = typeof body.errorCode === 'string' ? body.errorCode : 'unexpected_error'
    if (response.status === 403 && errorCode === PASSWORD_CHANGE_REQUIRED) {
      passwordChangeHandler?.()
    }
    throw new ApiError(
      response.status,
      errorCode,
      body.params ?? {},
      body.message ?? response.statusText,
    )
  }
  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}
