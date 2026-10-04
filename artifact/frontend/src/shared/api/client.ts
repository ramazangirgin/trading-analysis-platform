import { ApiError } from './error'

/**
 * The HTTP client. Only the features' `api.ts` modules call it
 * (docs/coding-convention/frontend-folder-structure.md).
 */
export async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response
  try {
    response = await fetch(path, {
      ...init,
      headers: {
        Accept: 'application/json',
        ...(init?.body ? { 'Content-Type': 'application/json' } : {}),
      },
    })
  } catch (e) {
    throw new ApiError(0, 'network_error', {}, String(e))
  }
  if (!response.ok) {
    const body = await response.json().catch(() => ({}))
    throw new ApiError(
      response.status,
      typeof body.errorCode === 'string' ? body.errorCode : 'unexpected_error',
      body.params ?? {},
      body.message ?? response.statusText,
    )
  }
  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}
