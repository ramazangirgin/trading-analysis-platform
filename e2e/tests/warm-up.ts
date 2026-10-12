import type { APIRequestContext } from '@playwright/test'

/**
 * Warms the backend's catalog and engine version cache before the tests: the first request starts
 * ta-runner, which imports TradingAgents and takes seconds (more on a busy CI machine). The
 * context must be allowed to call the API (logged in).
 */
export async function warmUpApi(api: APIRequestContext) {
  for (const path of ['/api/catalog', '/api/health']) {
    const response = await api.get(path, { timeout: 120_000 })
    if (!response.ok()) throw new Error(`${path}: ${response.status()} ${await response.text()}`)
  }
}
