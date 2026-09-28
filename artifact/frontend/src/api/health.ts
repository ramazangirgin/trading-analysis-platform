export type BackendStatus = 'up' | 'down'

// Placeholder until the typed client is generated from /api/openapi (Phase 1).
export async function fetchBackendStatus(signal?: AbortSignal): Promise<BackendStatus> {
  try {
    const response = await fetch('/actuator/health', { signal })
    if (!response.ok) return 'down'
    const body: { status?: string } = await response.json()
    return body.status === 'UP' ? 'up' : 'down'
  } catch {
    return 'down'
  }
}
