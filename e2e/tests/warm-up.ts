import { request, type APIRequestContext, type FullConfig } from '@playwright/test'

/**
 * Warms the backend's catalog and engine version cache before the tests: the first request starts
 * ta-runner, which imports TradingAgents and takes seconds (more on a busy CI machine). The
 * context must be allowed to call the API (logged in, when login is on).
 */
export async function warmUpApi(api: APIRequestContext) {
  for (const path of ['/api/catalog', '/api/health']) {
    const response = await api.get(path, { timeout: 120_000 })
    if (!response.ok()) throw new Error(`${path}: ${response.status()} ${await response.text()}`)
  }
}

/** The screenshots' global setup: login is off there, so the warm-up needs nothing else. */
export default async function warmUp(config: FullConfig) {
  const api = await request.newContext({ baseURL: config.projects[0]!.use.baseURL })
  try {
    await warmUpApi(api)
  } finally {
    await api.dispose()
  }
}
